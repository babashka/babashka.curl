(ns babashka.curl
  (:refer-clojure :exclude [get])
  (:require #_[clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.lang ProcessBuilder$Redirect]))

(defn- shell-command
  "Executes shell command.
  Accepts the following options:
  `:input`: instead of reading from stdin, read from this string.
  `:to-string?`: instead of writing to stdoud, write to a string and
  return it.
  `:throw?`: Unless `false`, exits script when the shell-command has a
  non-zero exit code, unless `throw?` is set to false."
  ([args] (shell-command args nil))
  ([args {:keys [:throw?]
          :or {throw? true}}]
   (let [pb (let [pb (ProcessBuilder. ^java.util.List args)]
              (doto pb
                (.redirectInput ProcessBuilder$Redirect/INHERIT)
                (.redirectError ProcessBuilder$Redirect/INHERIT)))
         proc (.start pb)
         string-out
         (let [sw (java.io.StringWriter.)]
           (with-open [w (io/reader (.getInputStream proc))]
             (io/copy w sw))
           (str sw))
         exit-code (.waitFor proc)]
     (when (and throw? (not (zero? exit-code)))
       (throw (ex-info "Got non-zero exit code" {:status exit-code})))
     {:out string-out
      :exit exit-code
      :err ""})))

(defn exec-curl [args opts]
  (let [res (shell-command args opts)
        exit (:exit res)
        out (:out res)]
    ;; TODO: handle non-zero exit with exception?  TODO: should we return a map
    ;; with a :body or just the body?  I think the latter is what I want 99% of
    ;; the time, so maybe the first should be supported via an option?
    out))

(defn curl-args [opts]
  (let [method (some-> (:method opts) name str/upper-case)
        method (when method ["-X" method])
        headers (:headers opts)
        headers (loop [headers* (transient [])
                       kvs (seq headers)]
                  (if kvs
                    (let [[k v] (first kvs)]
                      (recur (reduce conj! headers* ["-H" (str k ": " v)]) (next kvs)))
                    (persistent! headers*)))
        data-raw (:data-raw opts)
        data-raw (when data-raw
                   ["--data-raw" data-raw])
        url (:url opts)
        in-file (:in-file opts)
        in-file (when in-file ["-d" (str "@" (.getCanonicalPath ^java.io.File in-file))])]
    (conj (reduce into ["curl" "--silent" "--show-error"] [method headers data-raw in-file (:raw-args opts)])
          url)))

(defn request [opts]
  (let [args (curl-args opts)]
    (when (:debug? opts)
      (println (str/join " " (map pr-str (cons "curl" args)))))
    (exec-curl args opts)))

(defn get
  ([url] (get url nil))
  ([url opts]
   (let [opts (assoc opts :url url)]
     (request opts))))

(defn file? [f]
  (let [f (io/file f)]
    (and (.exists f)
         (.isFile f))))

(defn post
  ([url] (post url nil))
  ([url opts]
   (let [opts (assoc opts :url url
                     :method :post)
         body (:body opts)
         opts (if body
                (cond-> opts
                    (string? body) (assoc :data-raw body)
                    (file? body) (assoc :in-file body))
                opts)]
     (request opts))))

(defn put
  ([url] (put url nil))
  ([url opts]
   (let [opts (assoc opts :url url
                     :method :put)]
     (request opts))))
