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
  [args]
  (let [pb (let [pb (ProcessBuilder. ^java.util.List args)]
             (doto pb
               (.redirectInput ProcessBuilder$Redirect/INHERIT)
               (.redirectError ProcessBuilder$Redirect/INHERIT)))
        proc (.start pb)
        out (.getInputStream proc)]
    {:out out
     :proc proc}))

(defn- exec-curl [args opts]
  (let [res (shell-command args)
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
    (conj (reduce into ["curl" "--silent" "--show-error" "--location" "--include"]
                  [method headers accept-header data-raw in-file basic-auth
                   form-params (:raw-args opts)])
          (str url
               (when query-params
                 (str "?" query-params))))))

;;;; End utils

;;;; Response Parsing

;; See https://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/src/share/classes/java/io/DataInputStream.java#l501
(defn- read-line [^PushbackInputStream is]
  (let [sb (StringBuilder.)]
    (loop []
      (let [c1 (.read is)]
        (case c1
          ;; 10 = \n, 13 = \r
          (-1 10) :done
          13 (let [c2 (.read is)]
               (when (and (not= c2 10) (not= c2 -1))
                 (.unread is c2)))
          (do (.append sb (char c1))
              (recur)))))
    (str sb)))

(defn- read-headers [^PushbackInputStream is]
  (loop [headers []]
    (let [next-line (read-line is)]
      (if (str/blank? next-line)
        headers
        (recur (conj headers next-line))))))

(defn- curl-response->map
  "Parses a curl response input stream into a map"
  [^java.io.InputStream input-stream opts]
  (let [input-stream (PushbackInputStream. input-stream)]
    (loop [redirects []]
      (let [headers (read-headers input-stream)
            [status-line & header-lines] headers
            status   (Integer/parseInt (second (str/split status-line  #" ")))
            headers  (reduce (fn [acc header-line]
                               (let [[k v] (str/split header-line #":" 2)]
                                 (if (and k v)
                                   (assoc acc (str/lower-case k) (str/trim v))
                                   acc)))
                             {}
                             header-lines)
            response {:status  status
                      :headers headers}]
        (if (and (get-in response [:headers "location"])
                 (pos? (.available input-stream)))
          ;; stream not ended, assume curl must be following the redirect
          (recur (conj redirects response))
          (let [response (if (seq redirects)
                           (assoc response :redirects redirects)
                           response)
                body (if (identical? :stream (:as opts))
                       input-stream
                       (slurp input-stream))
                response (assoc response :body body)]
            response))))))

;;;; End Response Parsing

(defn request [opts]
  (let [args (curl-command opts)]
    (when (:debug? opts)
      (println (str/join " " (map pr-str args))))
    (-> (exec-curl args opts)
        (curl-response->map opts))))

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
