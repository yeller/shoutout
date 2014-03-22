(ns shoutout
  (:require [clojure.string :as s])
  (:import java.util.zip.CRC32))

(defn crc32 [^String s]
  (let [crc (CRC32.)]
    (.update crc (.getBytes s))
    (.getValue crc)))

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
              (into #{} (split raw-groups #","))
              (into #{} (split raw-users  #","))
              (parse-percentage raw-percentage))))

(defn serialize-feature [^Feature feature]
  (str
    (:percentage feature)
    "|"
    (s/join "," (:users feature))
    "|"
    (s/join "," (:groups feature))))

(defprotocol ShoutoutStorage
  (read-from-storage [storage ^String feature-name])
  (write-to-storage [storage  ^String feature-name ^String serialized]))

(defn get-from-storage [storage feature-name]
  (parse-feature feature-name (read-from-storage storage feature-name)))

(defn with-storage [storage feature-name f]
  (let [feature (get-from-storage storage feature-name)]
    (write-to-storage storage feature-name (serialize-feature
                                             (f feature)))))

(defn activate [{storage :storage} feature-name]
  (with-storage
    #(assoc %
            :percentage
            100)))

(defn deactivate [{storage :storage} feature-name]
  (with-storage
    #(assoc %
            :percentage
            0)))

(defn activate-group [{storage :storage} feature-name group]
  (with-storage
    (fn [feature]
      (update-in feature
                :groups
                 #(conj % group)))))

(defn deactivate-group [{storage :storage} feature-name group]
  (with-storage
    (fn [feature]
      (update-in feature
                 :groups
                 #(disj % group)))))

(defn activate-user [{storage :storage} feature-name user]
  (with-storage
    (fn [feature]
      (update-in feature
                :users
                 #(conj % user)))))

(defn deactivate-user [{storage :storage} feature-name user]
  (with-storage
    (fn [feature]
      (update-in feature
                 :users
                 #(disj % user)))))

(defn activate-percentage [{storage :storage} feature-name percent]
  (with-storage
    (fn [feature]
      (assoc feature
             :percentage
             percent))))

(defn is-active-in-percentage [{percentage :percentage} user]
  (< (mod (crc32 user) 100) percentage))

(defn is-active-user? [{active-users :users} user]
  (contains? active-users user))

(defn is-active-in-group? [{active-groups :groups} group-definition user]
  (some
    (fn [group-name]
      ((group-definition group-name (constantly false)) user))

    active-groups))

(defn active-feature? [feature group-definition user]
  (or
    (is-active-in-percentage feature user)
    (is-active-user? feature user)
    (is-active-in-group? feature group-definition user)))

(defn active? [{storage :storage groups :groups} feature-name user]
  (active-feature? (get-from-storage storage feature-name) groups user))

(defn shoutout [storage groups]
  {:storage storage
   :groups groups})
