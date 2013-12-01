(ns incise.server
  (:require (compojure [route :refer [files not-found]]
                       [core :refer [routes]])
            (hiccup [page :refer [html5]])
            (incise [config :as conf]
                    [load :refer [load-parsers-and-layouts]])
            (ring.middleware [reload :refer [wrap-reload]]
                             [incise :refer [wrap-incise]]
                             [stacktrace :refer [wrap-stacktrace-web]])
            [clojure.tools.nrepl.server :as nrepl]
            [stefon.core :refer [asset-pipeline]]
            [taoensso.timbre :refer [info error fatal]]
            [clojure.stacktrace :refer [print-cause-trace]]
            [org.httpkit.server :refer [run-server]]))

(defn wrap-static-index [handler]
  (fn [{:keys [uri] :as request}]
    (handler (if (= (last uri) \/)
               (assoc request :uri (str uri "index.html"))
               request))))

(defn wrap-log-exceptions [func & {:keys [bubble] :or {bubble true}}]
  "Log (i.e. print) exceptions received from the given function."
  (fn [& args]
    (try
      (apply func args)
      (catch Throwable e
        (error (with-out-str (print-cause-trace e)))
        (when bubble (throw e))))))

(defn create-app
  "Create a ring application that is a deverlopment friendly server."
  []
  (-> (routes (files "/" {:root (conf/get :out-dir)})
              (not-found (html5 [:h1 "404"])))
      (wrap-static-index)
      (wrap-reload :dirs ["src"])
      (wrap-incise)
      (wrap-log-exceptions)
      (wrap-stacktrace-web)
      (asset-pipeline nil)))

(defn- getenv
  "A nice wrapper around System/getenv that allows a second argument to be
  passed in as the default."
  [variable & [default]]
  (or (System/getenv variable) default))

(defn serve
  "Start a development server."
  []
  (let [port (Integer. (conf/get :port (getenv "PORT" 5000)))
        app (create-app)]
    (info "Serving at"
          (str "http://" (.getCanonicalHostName
                           (java.net.InetAddress/getLocalHost)) \: port \/))
    (run-server app {:port port
                     :thread (Integer.
                               (conf/get :thread-count
                                         (getenv "THREAD_COUNT" 4)))})))

;; ## Functions for manipulating the server atom.
(defonce server (atom nil))
(defonce nrepl-server (atom nil))

(defn serve-nrepl []
  (let [nrepl-config (merge {:port (inc (conf/get :port 5000))
                             :bind "127.0.0.1"}
                            (conf/get :nrepl))
        {:keys [port ss] :as server} (apply nrepl/start-server
                                            (flatten (seq nrepl-config)))]
    (info "Started nrepl server at" (str (-> ss
                                             (.getInetAddress)
                                             (.getCanonicalHostName)) \: port))
    (spit ".nrepl-port" port)
    server))

(defn start
  "Start a ring server and an nrepl server and bind the results to the server
  and nrepl-server-atoms atom."
  [& args]
  (reset! nrepl-server (apply serve-nrepl args))
  (reset! server (apply serve args)))

(defn stop []
  (when @server
    (@server)
    (reset! server nil)
    (nrepl/stop-server @nrepl-server)
    (reset! nrepl-server nil)))
