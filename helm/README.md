# Helmfile deployment

helm/helmfile.yaml.gotmpl is the deployment entry point for Kubernetes resources owned by this repository. It targets the existing default namespace and manages three releases: MySQL from Bitnami OCI chart 14.0.3, the Java application from local myappchart, and phpMyAdmin from local phpmyadminchart.

The ingress-nginx controller is external infrastructure and is not managed here. The application chart creates only Ingress/myapp, using the separately installed nginx IngressClass.

## Helmfile behavior

The bundle uses createNamespace: false, wait: true, atomic: true, and a 300 second timeout. Therefore default must already exist, Helm waits for release resources, and a failed install/upgrade is rolled back where Helm can do so. Database data and schema changes are not transactional rollbacks.

The Java image inputs are required Helmfile environment variables:

    APP_IMAGE=cr.yandex/<registry-id>/gradle-app
    APP_VERSION=<version-or-commit-sha>
    APP_IMAGE_DIGEST=sha256:<registry-digest>

With a digest, the application Deployment uses APP_IMAGE@APP_IMAGE_DIGEST. CI/CD supplies the image path from Jenkinsfile, the Git commit SHA as APP_VERSION, and the digest resolved after pushing to Yandex Container Registry. Local lint/template commands can use placeholders.

## Managed resources

myappchart renders a ConfigMap, three-replica Deployment, Service, and Ingress. The application listens on port 8080, has resource requests/limits and TCP probes, and runs as a non-root user without privilege escalation. Its database environment references the ConfigMap and mysql-secret.

The MySQL release uses replication with one primary and one secondary. Each instance has an enabled 5Gi persistent volume claim. The selected Bitnami values also enable the chart's NetworkPolicy and PodDisruptionBudgets, disable metrics, use an existing Secret for authentication, and select the pinned legacy MySQL image required by the chosen chart configuration.

phpMyAdmin runs as one replica, connects to MySQL through the shared ConfigMap, and is exposed by its own Kubernetes Service on port 8085. It is an auxiliary administration component, not part of the Java application's core request path.

## External Secret and access

Secret/mysql-secret must be provisioned before deployment in default. Helmfile and the charts reference it but do not create it. Required keys are:

    mysql-root-password
    mysql-password
    mysql-replication-password

Jenkins obtains a temporary kubeconfig with the Yandex Cloud CLI using a Yandex Cloud Service Account. Kubernetes authorization is limited to a namespace-scoped Role and RoleBinding for default; no ClusterRole or ClusterRoleBinding is required. These RBAC objects are provisioned externally because they are not stored in this repository.

## Local validation

From this directory, run without changing the cluster:

    APP_IMAGE=cr.yandex/<registry-id>/gradle-app APP_VERSION=dev APP_IMAGE_DIGEST=sha256:dev helmfile lint --skip-deps
    APP_IMAGE=cr.yandex/<registry-id>/gradle-app APP_VERSION=dev APP_IMAGE_DIGEST=sha256:dev helmfile template --skip-deps

Jenkins runs linting and rendering before helmfile apply --skip-deps --wait --timeout 300 --suppress-secrets, then checks the Java and phpMyAdmin Deployments, both MySQL StatefulSets, and the public GET /get-data smoke test.
