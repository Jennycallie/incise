(ns incise.core
  (:require (incise [load :refer [load-parsers-and-layouts]]
                    [config :as conf]
                    [once :refer [once]]
                    [utils :refer [delete-recursively]])
            [incise.deploy.core :refer [deploy]]
            [taoensso.timbre :refer [warn]]
            [clojure.string :as s]
            [clojure.tools.cli :refer [cli]]
            [incise.server :refer [wrap-log-exceptions serve stop-server
                                   defserver]]))

(def ^:private valid-methods #{"serve" "once" "deploy"})
(defn- parse-method [method]
  (if (contains? valid-methods method)
    (keyword method)
    (do
      (when-not (empty? method)
      (warn (str \" method
                 "\" is not a valid method (must be in "
                 (s/join ", " valid-methods) "). Defaulting to serve.")))
      :serve)))

(defn- with-args*
  "A helper function to with-args macro which does all the work.

   1.  Load in the config
   2.  Parse arguments
   3.  Merge into config
   4.  Handle help or continue"
  [args body-fn]
  (conf/load)
  (let [[options cli-args banner]
        (cli args
             "A tool for incising."
             ["-h" "--help" "Print this help." :default false :flag true]
             ["-m" "--method" "serve, once, or deploy"
              :default :serve :parse-fn parse-method]
             ["-i" "--in-dir" "The directory to get source from"]
             ["-o" "--out-dir" "The directory to put content into"])]
    (conf/merge options)
    (if (:help options)
      (do (println banner)
          (System/exit 0))
      ((wrap-log-exceptions body-fn) cli-args))))

(defmacro with-args
  "Take arguments parsing them using cli and handle help accordingly."
  [args & body]
  `(with-args* ~args (fn [~'args] ~@body)))

(defn -main
  "Start the development server and watcher."
  [& args]
  (with-args args
    (case (conf/get :method)
      :deploy (do ((wrap-log-exceptions deploy :bubble false))
                  (System/exit 0))
      :once (do ((wrap-log-exceptions (partial once false)
                                      :bubble false))
                (System/exit 0))
      (serve))))

(defn restart
  "Stop a server if it is already started and start a new one."
  [& args]
  (stop-server)
  (require :reload 'incise.server)
  (apply defserver args))
