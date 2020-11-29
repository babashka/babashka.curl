(defproject babashka/babashka.curl "0.0.1"
  :description "Clojure wrapper for curl"
  :url "https://github.com/babashka/babashka.curl"
  :scm {:name "git"
        :url "https://github.com/babashka/babashka.curl"}
  :license {:name "EPL-1.0"
            :url "https://www.eclipse.org/legal/epl-1.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass
                                    :sign-releases false}]])
