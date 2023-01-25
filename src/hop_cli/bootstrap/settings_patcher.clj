(ns hop-cli.bootstrap.settings-patcher
  (:require [clojure.walk :as walk]
            [hop-cli.bootstrap.util :as bp.util]
            [hop-cli.util :as util]
            [malli.core :as m]))

(defn- add-root-settings-node
  [settings]
  (if-not (vector? settings)
    settings
    {:name :settings
     :type :root
     :version "1.0"
     :value settings}))

(defn- cloud-provider->deployment-target
  [node]
  (if-not (map? node)
    node
    (case (:type node)
      :ref
      (update node :value (fn [ref-key]
                            (-> ref-key
                                (bp.util/settings-kw->settings-path)
                                (bp.util/swap-key-in-kw-path :cloud-provider :deployment-target)
                                (bp.util/settings-path->settings-kw))))

      (:plain-group :single-choice-group :multiple-choice-group)
      (update node :name (fn [node-name]
                           (if (= node-name :cloud-provider)
                             :deployment-target
                             node-name)))
      node)))

(def ^:private versions-schema
  [:map
   [:cli-version [:maybe string?]]
   [:settings-version [:maybe string?]]])

(def ^:private patches
  [{:patch-schema [:and
                   versions-schema
                   [:fn '(fn [{:keys [cli-version settings-version]}]
                           (and (zero? (compare cli-version "0.1.2"))
                                (neg-int? (compare settings-version "1.0"))))]]
    :patch-fn cloud-provider->deployment-target}])

(defn- build-appliable-patches-fn
  [settings]
  (let [cli-version (util/get-version)
        settings-version (:version settings)
        appliable-patches-fns (reduce (fn [patch-fns {:keys [patch-schema patch-fn]}]
                                        (if (m/validate patch-schema {:cli-version cli-version
                                                                      :settings-version settings-version})
                                          (conj patch-fns patch-fn)
                                          patch-fns))
                                      []
                                      patches)]
    (when (seq appliable-patches-fns)
      ;; Beware that the order of execution is reversed with
      ;; `comp`. So it won't follow the same order as in `patches`
      ;; data structure.
      (apply comp appliable-patches-fns))))

(def ^:private cli-settings-version-compatibility-mapping
  {"0.1.2" #(or (neg-int? (compare % "1.0"))
                (zero? (compare % "1.0")))})

(defn cli-and-settings-version-compatible?
  [settings]
  (let [cli-version (util/get-version)
        settings-version (:version settings)]
    ((get cli-settings-version-compatibility-mapping cli-version nil?) settings-version)))

(defn apply-patches
  [settings]
  (let [appliable-patches-fn (build-appliable-patches-fn settings)]
    (-> (if (fn? appliable-patches-fn)
          (walk/prewalk appliable-patches-fn settings)
          settings)
        ;; Special patch that adds a root node to settings if it does
        ;; not have it.
        add-root-settings-node)))