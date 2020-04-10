(ns babashka.curl
  (:refer-clojure :exclude [get read-line])
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.lang ProcessBuilder$Redirect]
           [java.net URLEncoder]
           [java.net URI]
           [java.io File PushbackInputStream]))

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
                     ["--user" basic-auth])
        header-file (.getPath ^File (:header-file opts))]
    (conj (reduce into ["curl" "--silent" "--show-error" "--location" "--dump-header" header-file]
                  [method headers accept-header data-raw in-file basic-auth
                   form-params (:raw-args opts)])
          (str url
               (when query-params
                 (str "?" query-params))))))

;;;; End utils

;;;; Response Parsing

(defn- read-headers [^File header-file]
  (line-seq (io/reader header-file)))

(defn- curl-response->map
  "Parses a curl response input stream into a map"
  [^java.io.InputStream input-stream opts]
  (let [;; use PushbackInputstream to start reading, else curl won't do anything
        pbis (PushbackInputStream. input-stream)
        _ (let [c (.read pbis)]
            (when-not (= -1 c)
              (.unread pbis c)))
        body (if (identical? :stream (:as opts))
               pbis
               (slurp pbis))
        headers (read-headers (:header-file opts))
        [status headers]
        (reduce (fn [[status parsed-headers :as acc] header-line]
                  (if (str/starts-with? header-line "HTTP/")
                    [(Integer/parseInt (second (str/split header-line  #" "))) parsed-headers]
                    (let [[k v] (str/split header-line #":" 2)]
                      (if (and k v)
                        [status (assoc parsed-headers (str/lower-case k) (str/trim v))]
                        acc))))
                [nil {}]
                headers)
        response {:status status
                  :headers headers
                  :body body}]
    response))

;;;; End Response Parsing

(defn request [opts]
  (let [header-file (File/createTempFile "babashka.curl" ".headers")
        opts (assoc opts :header-file header-file)
        args (curl-command opts)]
    (when (:debug? opts)
      (println (str/join " " (map pr-str args))))
    (let [response (-> (exec-curl args opts)
                       (curl-response->map opts))]
      (.delete header-file)
      response)))

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
