(ns shoutout
  (:require [clojure.string :as s])
  (:import java.util.zip.CRC32))

;; infrastructure
(defn crc32 [^String s]
  (let [crc (CRC32.)]
    (.update crc (.getBytes s))
    (.getValue crc)))

(defn split
  "splits strings using clojure.string/split, but doesn't return [\"\"] when
  splitting an empty string"
  [raw pattern]
  (if (empty? raw)
    []
    (s/split raw pattern)))

;; functional core
(defrecord Feature [feature-name groups users percentage])

(defprotocol HasUserId
  "see the doc string for shoutout/user-id"
  (user-id
    [this]
    "converts a user (of the protocol type) into a user id (which is just a
    string).
    this conversion happens in a few places:
      - to check if a user should be in the activated percentage
      - when marking a user as active for an individual feature
      - when checking if a user is marked as active for an feature"))

(extend-type String HasUserId
  (user-id [this] this))

(extend-type java.lang.Number HasUserId
  (user-id [this] (str this)))

(defn is-active-in-percentage [{percentage :percentage} user]
  (< (mod (crc32 (user-id user)) 100) percentage))

(defn is-active-user? [{active-users :users} user]
  (contains? active-users (user-id user)))

(defn is-active-in-group? [{active-groups :groups} group-definition user]
  (some
    (fn [group-name]
      ((group-definition group-name (constantly false)) user))

    active-groups))

(defn active-feature?
  "works out if a user is active in a feature, by checking percentages, if
  they're specifically marked as active, or if they're in a group that is
  active"
  [feature group-definition user]
  (or
    (is-active-in-percentage feature user)
    (is-active-user? feature user)
    (is-active-in-group? feature group-definition user)))

(defn parse-percentage [^String raw]
  (if (empty? raw)
    0
    (Integer/parseInt raw)))

(defn parse-feature
  "turns a feature name and serialized feature into an feature"
  [^String feature-name ^String raw]
  (let [[raw-percentage raw-users raw-groups]
        (split raw #"\|")]
    (Feature. feature-name
              (into #{} (split raw-groups #","))
              (into #{} (split raw-users  #","))
              (parse-percentage raw-percentage))))

(defn serialize-feature
  "turns a feature into a serialized feature, ready for storage"
  [^Feature feature]
  (str
    (:percentage feature)
    "|"
    (s/join "," (:users feature))
    "|"
    (s/join "," (:groups feature))))


;; imperative shell
(defprotocol ShoutoutStorage
  "an abstraction over storage for your feature toggles

  rough contract: after I put in a feature at feature-name, I should be able to
  get out a feature at that feature-name at some point in the future

  implementations of this protocol should NOT concern themselves with serialization,
  they deal with serialized strings only"
  (read-from-storage
    [storage ^String feature-name]
    "read the feature from the supplied storage. should return a raw String,
    ready to be parsed by parse-feature")
  (write-to-storage
    [storage  ^String feature-name ^String serialized]
    "write a serialized feature to the storage"))

(defn prefix-feature-name
  "prefixes \"shoutout_feature\" onto a string"
  [raw]
  (str "shoutout_feature" raw))

(defn get-from-storage
  "retrieve a deserialized feature from storage"
  [storage feature-name]
  (parse-feature feature-name
                 (or (read-from-storage storage (prefix-feature-name feature-name))
                     "||")))

(defn update-in-storage
  "alter a feature using the supplied function f. handles reading, serializing,
  deserializing, you just write f to modify the feature as wanted"
  [storage feature-name f]
  (let [feature (get-from-storage storage feature-name)]
    (write-to-storage storage
                      (prefix-feature-name feature-name)
                      (serialize-feature (f feature)))))

(defn activate
  "completely activate a feature for all users"
  [{storage :storage} feature-name]
  (update-in-storage
    storage
    feature-name
    #(assoc %
            :percentage
            100)))

(defn deactivate "completely turn off a feature for all users"
  [{storage :storage} feature-name]
  (update-in-storage
    storage
    feature-name
    #(assoc %
            :percentage 0
            :groups #{}
            :users #{})))

(defn activate-group
  "activate a feature for a particular group.
  A group should just be a string"
  [{storage :storage} feature-name group]
  (update-in-storage
    storage
    feature-name
    (fn [feature]
      (update-in feature
                [:groups]
                 #(conj % group)))))

(defn deactivate-group
  "deactivate a feature for a particular group.
  A group is just a string"
  [{storage :storage} feature-name group]
  (update-in-storage
    storage
    feature-name
    (fn [feature]
      (update-in feature
                 [:groups]
                 #(disj % group)))))

(defn activate-user
  "activate a feature for a particular user"
  [{storage :storage} feature-name user]
  (update-in-storage
    storage
    feature-name
    (fn [feature]
      (update-in feature
                [:users]
                 #(conj % (user-id user))))))

(defn deactivate-user
  "deactivate a feature for a particular user"
  [{storage :storage} feature-name user]
  (update-in-storage
    storage
    feature-name
    (fn [feature]
      (update-in feature
                 [:users]
                 #(disj % (user-id user))))))

(defn activate-percentage
  "activate a feature for a percentage of users.
  percentages are out of 100"
  [{storage :storage} feature-name percent]
  (update-in-storage
    storage
    feature-name
    (fn [feature]
      (assoc feature
             :percentage
             percent))))

(defn active?
  "check if a particular user is active for a given feature"
  [{storage :storage groups :groups :as foo} feature-name user]
  (active-feature? (get-from-storage storage feature-name) groups user))

(defn shoutout
  "create a shoutout given some storage that implements ShoutoutStorage,
  and an optional set of group definitions. Group definitions are a map of
  group names (as strings) to functions that check if a user is in the group"
  ([storage] (shoutout storage {}))
  ([storage groups]
   {:storage storage
    :groups groups}))

(deftype InMemoryStorage [store-atom]
  Object
  (toString [_]
    (str "InMemoryStore: " @store-atom))
  ShoutoutStorage
  (read-from-storage [_ feature-name]
    (get @store-atom feature-name))
  (write-to-storage [_ feature-name value]
    (swap! store-atom assoc feature-name value)))

(defn in-memory-store []
  (InMemoryStorage. (atom {})))
