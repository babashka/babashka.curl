(ns babashka.curl
  (:refer-clojure :exclude [get read-line])
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.lang ProcessBuilder$Redirect]
           [java.net URLEncoder]
           [java.net URI]
           [java.io PushbackInputStream]))

(set! *warn-on-reflection* true)

;;;; Utils

(defn- shell-command
  "Executes shell command.
  Accepts the following options:
  `:input`: instead of reading from stdin, read from this string.
  `:to-string?`: instead of writing to stdoud, write to a string and
  return it.
  `:throw?`: Unless `false`, exits script when the shell-command has a
  non-zero exit code, unless `throw?` is set to false."
  ([args] (shell-command args nil))
  ([args {:keys [:throw? :as-stream?]
          :or {throw? true}}]
   (let [pb (let [pb (ProcessBuilder. ^java.util.List args)]
              (doto pb
                (.redirectInput ProcessBuilder$Redirect/INHERIT)
                (.redirectError ProcessBuilder$Redirect/INHERIT)))
         proc (.start pb)
         out
         (if as-stream? (.getInputStream proc)
             (let [sw (java.io.StringWriter.)]
               (with-open [w (io/reader (.getInputStream proc))]
                 (io/copy w sw))
               (str sw)))
         exit-code (when-not as-stream? (.waitFor proc))]
     (when (and throw?
                (not as-stream?)
                (not (zero? exit-code)))
       (throw (ex-info "Got non-zero exit code" {:status exit-code})))
     {:out out
      :exit exit-code
      :err ""})))

(defn- exec-curl [args opts]
  (let [res (shell-command args opts)
        exit (:exit res)
        out (:out res)]
    ;; TODO: handle non-zero exit with exception?  TODO: should we return a map
    ;; with a :body or just the body?  I think the latter is what I want 99% of
    ;; the time, so maybe the first should be supported via an option?
    out))

(defn- file? [f]
  (let [f (io/file f)]
    (and (.exists f)
         (.isFile f))))

(defn- accept-header [opts]
  (when-let [accept (:accept opts)]
    ["-H" (str "Accept: " (case accept
                            :json "application/json"
                            accept))]))

(defn- url-encode
  "Returns an UTF-8 URL encoded version of the given string."
  [^String unencoded]
  (URLEncoder/encode unencoded "UTF-8"))

(defn curl-command [opts]
  (let [body (:body opts)
        opts (if body
               (cond-> opts
                 (string? body) (assoc :data-raw body)
                 (file? body) (assoc :in-file body))
               opts)
        method (when-let [method (:method opts)]
                 (case method
                   :head ["--head"]
                   ["-X" (-> method name str/upper-case)]))
        headers (:headers opts)
        headers (loop [headers* (transient [])
                       kvs (seq headers)]
                  (if kvs
                    (let [[k v] (first kvs)]
                      (recur (reduce conj! headers* ["-H" (str k ": " v)]) (next kvs)))
                    (persistent! headers*)))
        accept-header (accept-header opts)
        form-params (:form-params opts)
        form-params (loop [params* (transient [])
                           kvs (seq form-params)]
                      (if kvs
                        (let [[k v] (first kvs)]
                          (recur (reduce conj! params* ["-F" (str k "=" v)]) (next kvs)))
                        (persistent! params*)))
        query-params (when-let [qp (:query-params opts)]
                       (loop [params* (transient [])
                              kvs (seq qp)]
                         (if kvs
                           (let [[k v] (first kvs)]
                             (recur (conj! params* (str (url-encode k) "=" (url-encode v))) (next kvs)))
                           (str/join "&" (persistent! params*)))))
        data-raw (:data-raw opts)
        data-raw (when data-raw
                   ["--data-raw" data-raw])
        url (let [url* (:url opts)]
              (cond
                (string? url*)
                url*

                (map? url*)
                (str (URI. ^String (:scheme url*)
                           ^String (:user url*)
                           ^String (:host url*)
                           ^Integer (:port url*)
                           ^String (:path url*)
                           ^String (:query url*)
                           ^String (:fragment url*)))))
        in-file (:in-file opts)
        in-file (when in-file ["-d" (str "@" (.getCanonicalPath ^java.io.File in-file))])
        basic-auth (:basic-auth opts)
        basic-auth (if (sequential? basic-auth)
                     (str/join ":" basic-auth)
                     basic-auth)
        basic-auth (when basic-auth
                     ["--user" basic-auth])]
    (conj (reduce into ["curl" "--silent" "--show-error" "--location"]
                  [method headers accept-header data-raw in-file basic-auth
                   form-params (:raw-args opts)])
          (str url
               (when query-params
                 (str "?" query-params))))))

;;;; End utils

;;;; Response Parsing
(defn- assoc-if-not-empty [map key val]
  (if (empty? val)
    map
    (assoc map key val)))

(defn- input-stream-eof? [^java.io.InputStream rdr]
  (.mark rdr 1)
  (let [eof? (= -1 (.read rdr))]
    (when-not eof?
      (.reset rdr))
    eof?))

(defn- read-line [^java.io.InputStream is]
  (let [sb (StringBuilder.)
        res (loop [in is]
              (let [c1 (.read is)]
                (case c1
                  ;; 10 = \n, 13 = \r
                  (-1 10) (str sb)
                  13 (let [c2 (.read is)]
                        (when (and (not= c2 10) (not= c2 -1))
                          (let [in (PushbackInputStream. is)]
                            (.unread in c2)))
                        (str sb))
                  (do (.append sb (char c1))
                      (recur in)))))]
    res))

(defn- curl-response->map
  "Parses a curl response input stream into a map"
  [input-stream opts]
  (loop [redirects []]
    (let [[status-line & header-lines]
          (take-while #(not (str/blank? %)) (repeatedly #(read-line input-stream)))
          status   (Integer/parseInt (second (str/split status-line  #" ")))
          headers  (reduce (fn [acc header-line]
                             (let [[k v] (str/split header-line #":" 2)]
                               (assoc acc (str/lower-case k) (str/trim v))))
                           {}
                           header-lines)
          response {:status  status
                    :headers headers}]
      (if (and (get-in response [:headers "location"])
               (not (input-stream-eof? input-stream)))
        ;; stream not ended, assume curl must be following the redirect
        (recur (conj redirects response))
        (-> response
            (assoc-if-not-empty :redirects redirects)
            (assoc :body (if (identical? :stream (:as opts))
                           input-stream
                           (slurp input-stream))))))))

;;;; End Response Parsing

(defn request [opts]
  (let [args (curl-command opts)
        stream? (identical? :stream (:as opts))]
    (when (:debug? opts)
      (println (str/join " " (map pr-str args))))
    (if (:response opts)
      (curl-response->map (exec-curl (conj args "--include") (assoc opts :as-stream? true)) opts)
      (exec-curl args (assoc opts :as-stream? stream?)))))

(defn head
  ([url] (head url nil))
  ([url opts]
   (let [opts (assoc opts :url url
                     :method :head)]
     (request opts))))

(defn get
  ([url] (get url nil))
  ([url opts]
   (let [opts (assoc opts :url url)]
     (request opts))))

(defn post
  ([url] (post url nil))
  ([url opts]
   (let [opts (assoc opts :url url
                     :method :post)]
     (request opts))))

(defn put
  ([url] (put url nil))
  ([url opts]
   (let [opts (assoc opts :url url
                     :method :put)]
     (request opts))))

(defn patch
  ([url] (patch url nil))
  ([url opts]
   (let [opts (assoc opts :url url
                     :method :patch)]
     (request opts))))

(comment
  ;; after running a python server in the source repo with `python3 -m http.server`
  (request {:url      {:host   "localhost"
                       :scheme "http"
                       :port   8000
                       :path   "/src/babashka"}
            :raw-args ["-L"]})

  (request {:url      {:host   "localhost"
                       :scheme "http"
                       :port   8000
                       :path   "/src/babashka"}
            :response true})

    )
