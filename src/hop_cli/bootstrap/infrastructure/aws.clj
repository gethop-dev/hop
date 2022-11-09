(ns hop-cli.bootstrap.infrastructure.aws
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [hop-cli.aws.cloudformation :as aws.cloudformation]
            [hop-cli.aws.ssl :as aws.ssl]
            [hop-cli.util.thread-transactions :as tht]))

(def ^:const cfn-templates-path
  (io/resource "infrastructure/cloudformation-templates"))

(def cfn-templates
  {:account {:master-template "account.yaml"
             :capability :CAPABILITY_NAMED_IAM
             :stack-name-kw :aws.account/stack-name
             :input-parameter-mapping
             {:aws.account.vpc/cidr :VpcCIDR
              :aws.account/resource-name-prefix :ResourceNamePrefix}
             :output-parameter-mapping
             {:EbServiceRoleARN :aws.account.iam/eb-service-role-arn
              :LocalDevUserARN :aws.account.iam/local-dev-user-arn
              :RDSMonitoringRoleARN :aws.account.iam/rds-monitoring-role-arm
              :PublicRouteTable1Id :aws.account.vpc/public-route-table-id
              :VpcId :aws.account.vpc/id}}

   :project {:master-template "project.yaml"
             :capability :CAPABILITY_NAMED_IAM
             :stack-name-kw :aws.project/stack-name
             :input-parameter-mapping
             {:aws.account.vpc/id :VpcId
              :aws.account.vpc/public-route-table-id :PublicRouteTable1Id
              :aws.project.vpc.subnet-1/cidr :Subnet1CIDR
              :aws.project.vpc.subnet-2/cidr :Subnet2CIDR
              :aws.project.elb/certificate-arn :ElbCertificateArn}
             :output-parameter-mapping
             {:EbApplicationName :aws.project.eb/application-name
              :ElbSecurityGroupId :aws.project.elb/security-group-id
              :LoadBalancerARN  :aws.project.elb/arn
              :SubnetIds :aws.project.vpc/subnet-ids}}

   :dev-env {:master-template "local-environment.yaml"
             :capability :CAPABILITY_NAMED_IAM
             :stack-name-kw :aws.environment.dev/stack-name
             :environment "dev"
             :input-parameter-mapping
             {:aws.account.iam/local-dev-user-arn :LocalDevUserARN}
             :output-parameter-mapping
             {:CognitoUserPoolId :aws.environment.dev.cognito/user-pool-id
              :CognitoUserPoolURL :aws.environment.dev.cognito/user-pool-url
              :CognitoSPAClientId :aws.environment.dev.cognito/spa-client-id}}

   :test-env {:master-template "cloud-environment.yaml"
              :capability :CAPABILITY_NAMED_IAM
              :stack-name-kw :aws.environment.test/stack-name
              :environment "test"
              :input-parameter-mapping
              {:aws.environment.test/notifications-email :NotificationsEmail
               :aws.environment.test.database/version :DatabaseEngineVersion
               :aws.environment.test.database/password :DatabasePassword
               :aws.account.iam/rds-monitoring-role-arm :RDSMonitoringRoleARN
               :aws.account.vpc/id :VpcId
               :aws.project.vpc/subnet-ids :SubnetIds
               :aws.account.iam/eb-service-role-arn :EbServiceRoleARN
               :aws.project.eb/application-name  :EbApplicationName
               :aws.project.elb/arn :LoadBalancerARN
               :aws.project.elb/security-group-id :ElbSecurityGroupId}
              :output-parameter-mapping
              {:CognitoUserPoolId :aws.environment.test.cognito/user-pool-id
               :CognitoUserPoolURL :aws.environment.test.cognito/user-pool-url
               :CognitoSPAClientId :aws.environment.test.cognito/spa-client-id
               :RdsAddress :aws.environment.test.rds/address
               :EbEnvironmentName :aws.environment.test.eb/environment-name
               :EbEnvironmentURL :aws.environment.test.eb/environment-url}}

   :prod-env {:master-template "cloud-environment.yaml"
              :capability :CAPABILITY_NAMED_IAM
              :stack-name-kw :aws.environment.prod/stack-name
              :environment "prod"
              :input-parameter-mapping
              {:aws.environment.test/notifications-email :NotificationsEmail
               :aws.environment.test.database/version :DatabaseEngineVersion
               :aws.environment.test.database/password :DatabasePassword
               :aws.account.iam/rds-monitoring-role-arm :RDSMonitoringRoleARN
               :aws.account.vpc/id :VpcId
               :aws.project.vpc/subnet-ids :SubnetIds
               :aws.account.iam/eb-service-role-arn :EbServiceRoleARN
               :aws.project.eb/application-name  :EbApplicationName
               :aws.project.elb/arn :LoadBalancerARN
               :aws.project.elb/security-group-id :ElbSecurityGroupId}
              :output-parameter-mapping
              {:CognitoUserPoolId :aws.environment.test.cognito/user-pool-id
               :CognitoUserPoolURL :aws.environment.test.cognito/user-pool-url
               :CognitoSPAClientId :aws.environment.test.cognito/spa-client-id
               :RdsAddress :aws.environment.test.rds/address
               :EbEnvironmentName :aws.environment.test.eb/environment-name
               :EbEnvironmentURL :aws.environment.test.eb/environment-url}}})

(defn wait-for-stack-completion
  [stack-name]
  (loop []
    (let [result (aws.cloudformation/describe-stack {:stack-name stack-name})
          status (get-in result [:stack :status])]
      (cond
        (= status :CREATE_IN_PROGRESS)
        (do
          (println (format "%s stack creation in progress. Rechecking the status in 10 seconds..." stack-name))
          (Thread/sleep 10000)
          (recur))

        (= status :CREATE_COMPLETE)
        {:success? true
         :outputs (get-in result [:stack :outputs])}

        :else
        {:success? false
         :error-details result}))))

(defn- select-and-rename-keys
  [m mapping]
  (-> m
      (select-keys (keys mapping))
      (set/rename-keys mapping)))

(defn- provision-cfn-stack
  [settings {:keys [input-parameter-mapping output-parameter-mapping stack-name-kw] :as template-opts}]
  (let [stack-name (get settings stack-name-kw)
        project-name (:project/name settings)
        bucket-name (:aws.cloudformation/template-bucket-name settings)
        parameters (select-and-rename-keys settings input-parameter-mapping)
        opts (assoc template-opts
                    :project-name project-name
                    :parameters parameters
                    :stack-name stack-name
                    :s3-bucket-name bucket-name)
        _log (println (format "Provisioning cloudformation %s stack..." stack-name))
        result (aws.cloudformation/create-stack opts)]
    (if (:success? result)
      (let [wait-result (wait-for-stack-completion stack-name)]
        (if-not (:success? wait-result)
          wait-result
          (let [outputs (:outputs wait-result)
                new-settings (select-and-rename-keys outputs output-parameter-mapping)
                updated-settings (merge settings new-settings)]
            {:success? true
             :settings updated-settings})))
      result)))

(defn provision-initial-infrastructure
  [settings]
  (->
   [{:txn-fn
     (fn upload-cloudformation-templates
       [_]
       (let [bucket-name (:aws.cloudformation/template-bucket-name settings)
             opts {:bucket-name bucket-name
                   :directory-path cfn-templates-path}
             _log (println (format "Uploading cloudformation templates to %s bucket..." bucket-name))
             result (aws.cloudformation/update-templates opts)]
         (if (:success? result)
           {:success? true}
           {:success? false
            :reason :could-not-upload-cfn-templates
            :error-details result})))}
    {:txn-fn
     (fn provision-account
       [_]
       (let [{:keys [stack-name-kw output-parameter-mapping] :as template-opts} (:account cfn-templates)
             stack-name (get settings stack-name-kw)
             result (aws.cloudformation/describe-stack {:stack-name stack-name})]
         (if (and (:success? result) (:stack result))
           (let [outputs (get-in result [:stack :outputs])
                 new-settings (select-and-rename-keys outputs output-parameter-mapping)
                 updated-settings (merge settings new-settings)]
             (println "Skipping account stack creation because it already exists")
             {:success? true
              :settings updated-settings})
           (let [result (provision-cfn-stack settings template-opts)]
             (if (:success? result)
               {:success? true
                :settings (:settings result)}
               {:success? false
                :reason :could-not-provision-account-cfn
                :error-details result})))))}
    {:txn-fn
     (fn create-and-upload-self-signed-certificate
       [{:keys [settings]}]
       (if (:aws.project.elb/certificate-arn settings)
         (do
           (println "Skipping self-signed certificate upload.")
           {:success? true
            :settings settings})
         (let [_log (println "Creating and uploading self-signed certificate...")
               result (aws.ssl/create-and-upload-self-signed-certificate {})]
           (if (:success? result)
             (let [certificate-arn (:certificate-arn result)
                   updated-settings (assoc settings
                                           :aws.project.elb/certificate-arn certificate-arn)]
               {:success? true
                :settings updated-settings})
             {:success? false
              :reason :could-not-create-and-upload-self-signed-certificate
              :error-details result}))))}
    {:txn-fn
     (fn provision-project
       [{:keys [settings]}]
       (let [result (provision-cfn-stack settings (:project cfn-templates))]
         (if (:success? result)
           {:success? true
            :settings (:settings result)}
           {:success? false
            :reason :could-not-provision-project-cfn
            :error-details result})))}
    {:txn-fn
     (fn provision-dev-env
       [{:keys [settings]}]
       (let [result (provision-cfn-stack settings (:dev-env cfn-templates))]
         (if (:success? result)
           {:success? true
            :settings (:settings result)}
           {:success? false
            :reason :could-not-provision-dev-env
            :error-details result})))}
    {:txn-fn
     (fn provision-test-env
       [{:keys [settings]}]
       (let [result (provision-cfn-stack settings (:test-env cfn-templates))]
         (if (:success? result)
           {:success? true
            :settings (:settings result)}
           {:success? false
            :reason :could-not-provision-test-env
            :error-details result})))}]
   (tht/thread-transactions {})))

(defn provision-prod-infrastructure
  [settings]
  (provision-cfn-stack settings (:prod-env cfn-templates)))
