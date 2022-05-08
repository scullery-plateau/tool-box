(ns tool-box.core
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [tool-box.fight-club-five :as fc5]
            [clojure.pprint :as pp]))

(def cli-options
  [["-fc5" "--FightClub5" ]
   ["-fc5s" "--FightClub5Source FOLDER" :default fc5/root :parse-fn #(io/file %)]
   ["-fc5d" "--FightClub5Dest FILE" :default fc5/root :parse-fn #(io/file %)]
   ["-h" "--help"]])

(defn exit [status & msgs]
  (doseq [msg msgs]
    (println msg))
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (pp/pprint options)
    (cond
      errors (apply exit 1 errors)
      (:help options) (exit 0 summary)
      (:FightClub5 options) (fc5/process (:FightClub5Source options) (:FightClub5Dest options))
      (empty? options) (fc5/process fc5/root fc5/dest)
      :else (exit 1 summary))))
