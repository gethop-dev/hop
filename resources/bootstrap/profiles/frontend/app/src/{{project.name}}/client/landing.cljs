;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

{{=<< >>=}}
(ns <<project.name>>.client.landing
  (:require [<<project.name>>.client.view :as view]))

(defn- main []
  [:div "Landing"])

(defmethod view/view-display ::view
  [_]
  [main])
