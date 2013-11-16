(ns incise.once
  (:require (incise [load :refer [load-parsers-and-layouts]]
                    [config :as conf]
                    [utils :refer [delete-recursively directory?]])
            [clojure.java.io :refer [file]]
            [incise.parsers.core :refer [parse-all-input-files]]
            [taoensso.timbre :refer [info]]
            (stefon [settings :refer [with-options]]
                    [core :refer [precompile]])))

(defn once
  "Incise just once. This requires that config is already loaded."
  [& {:as config}]
  (conf/merge config)
  (conf/avow!)
  (let [out-dir (conf/get :out-dir)
        stefon-pre-opts {:mode :production
                         :serving-root out-dir
                         :precompiles (conf/get :precompiles)}]
    (info "Clearing out" (str \" out-dir \"))
    (delete-recursively (file out-dir))
    (with-options stefon-pre-opts
      (info "Precompiling assets...")
      (precompile)
      (info "Done.")
      (load-parsers-and-layouts)
      (doall (parse-all-input-files)))))
