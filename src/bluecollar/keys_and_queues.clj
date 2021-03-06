(ns bluecollar.keys-and-queues)

(def prefix
  ^{:doc "The prefix to all keys, lists, and data structures that pass through Redis.
          Feel free to change the name of this value if you see fit."}
  (atom "bluecollar"))

(defn setup-prefix [prefix-name] (reset! prefix (or prefix-name "bluecollar")))

(defn- prefix-key [key-name] (str @prefix ":" key-name))

(def ^{:doc "A map containing all the names of the keys used in bluecollar."
       :private true}
  key-registry (atom {}))

(def key-names #{"failure-retry-counter" "failure-total-counter"
                 "worker-runtimes" "success-total-counter"})

(defn failure-retry-counter-key
  ^{:doc "The name of the key where the retry count is stored for a failed job."}
  [] (get @key-registry "failure-retry-counter"))

(defn failure-total-counter-key
  ^{:doc "The name of the key where the total count of failed jobs is stored."}
  [] (get @key-registry "failure-total-counter"))

(defn worker-runtimes-key
  ^{:doc "The name of the key the execution runtimes are stored for workers."}
  [worker-name] (str (get @key-registry "worker-runtimes") ":" worker-name))

(defn success-total-counter-key
  ^{:doc "The name of the key where the total count of successful jobs is stored."}
  [] (get @key-registry "success-total-counter"))

(defn- register-key [key-name]
  (swap! key-registry assoc key-name (prefix-key key-name)))

(defn register-keys []
  (reset! key-registry nil)
  (doseq [key-name key-names] (register-key key-name)))

(def
  ^{:doc "A map containing the original queue names as keys and the values
          containing modified versions of the queue names
          as they are used internally to bluecollar."
    :private true}
  queue-registry (atom {}))

(def master-queue-name "master")
(def master-processing-queue-name "master-processing")
(def processing-queue-name "processing")

(defn- register-queue [queue-name]
  (swap! queue-registry assoc queue-name (prefix-key (str "queues:" queue-name))))

(defn fetch-queue [queue-name]
  (let [q (get @queue-registry queue-name)]
    (if (nil? q)
      (do
        (register-queue queue-name)
        (fetch-queue queue-name))
      q)))

(defn- register-processing-queues [instance-name]
  (doseq [queue [master-processing-queue-name processing-queue-name]]
    (swap! queue-registry assoc queue
      (prefix-key (str "queues:" (str queue "-" (or instance-name "default")))))))

(defn register-queues [queues instance-name]
  (reset! queue-registry nil)
  (register-queue master-queue-name)
  (register-processing-queues instance-name)
  (doseq [queue queues]
    (register-queue queue)))
