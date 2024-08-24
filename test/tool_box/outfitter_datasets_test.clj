(ns tool-box.outfitter-datasets-test 
  (:require [clojure.pprint :as pp]
            [clojure.test :as t]
            [tool-box.outfitter-datasets :as tbod]
            [cheshire.core :as json]))

(t/deftest test-get-dataset
  (t/testing "testing get-dataset"
    (let [resp (:body (tbod/get-dataset 0))
          data (json/parse-string resp true)]
      (pp/pprint (keys (:parts data))))))