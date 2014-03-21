(ns shoutout
  (:require [clojure.string :as s]))

(defrecord Feature [feature-name groups users percentage])

(defn split [raw pattern]
  (if (empty? raw)
    []
    (s/split raw pattern)))

(defn parse-percentage [^String raw]
  (if (empty? raw)
    0
    (Integer/parseInt raw)))

(defn parse-feature [^String feature-name ^String raw]
  (let [[raw-percentage raw-users raw-groups]
        (split raw #"\|")]
    (Feature. feature-name
              (into [] (split raw-groups #","))
              (into [] (split raw-users  #","))
              (parse-percentage raw-percentage))))

(defn serialize-feature [^Feature feature]
  (str
    (:percentage feature)
    "|"
    (s/join "," (:users feature))
    "|"
    (s/join "," (:groups feature))))
