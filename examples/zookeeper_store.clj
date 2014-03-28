(ns shoutout.zookeeper-store
  (:require shoutout
            [clojure.edn :as edn])
  (:import (org.apache.zookeeper CreateMode ZooKeeper)
           (org.apache.curator
             framework.CuratorFramework
             framework.CuratorFrameworkFactory
             retry.BoundedExponentialBackoffRetry
             framework.recipes.cache.NodeCache
             framework.recipes.cache.NodeCacheListener)))

(defn set-or-create [^CuratorFramework curator ^String k ^bytes v]
  (try
    (-> curator .create (.forPath k v))
    (catch org.apache.zookeeper.KeeperException$NodeExistsException e
      (-> curator .setData (.forPath k v)))))

(defn read-raw [^CuratorFramework curator ^String path]
  (try
    (edn/read-string (-> curator .getData (.forPath path)))
    (catch org.apache.zookeeper.KeeperException$NoNodeException e
      {})))

(deftype ShoutoutZookeeperStore [^CuratorFramework curator node-cache deserialized-cache path]
  shoutout/ShoutoutStorage
  (shoutout/read-from-storage [_ feature-name]
    (get @deserialized-cache feature-name))

  (shoutout/write-to-storage [_ feature-name serialized-feature]
    (let [existing (read-raw curator path)]
      (set-or-create
        curator
        path
        (pr-str
          (assoc existing
                 feature-name
                 serialized-feature))))))

(defn shoutout-cached-zookeeper-store [^CuratorFramework curator-framework ^String path]
  (let [deserialized-cache (atom {})
        node-cache (NodeCache. curator-framework path)]
    (.addListener
      (.getListenable node-cache)
      (reify NodeCacheListener
        (nodeChanged [_]
          (let [data (edn/read-string (.getData (.getCurrentData node-cache)))]
            (swap! deserialized-cache (constantly data))))))
    (.start node-cache)
    (.rebuild node-cache)
    (ShoutoutZookeeperStore. curator-framework node-cache deserialized-cache path)))
