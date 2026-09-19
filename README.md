# Java application delivery

This repository contains the Java 17 application, its Docker image definition,
and the Jenkins pipeline that builds and deploys it.

## Deployment flow

Jenkins builds the application with the repository's Gradle wrapper, tags the
image with the full Git commit SHA, records the registry digest, and deploys
that immutable image through the Helmfile in the `helm` directory.

The Helmfile directory is the only owner of the Kubernetes application
resources. The old raw Kubernetes manifests are no longer used.

The application uses its inline Jenkinsfile directly. The separate
`jenkins-sharedlib` repository is not required by this pipeline.

## Required cluster Secret

Before deployment, the target namespace must already contain a Secret named
`mysql-secret` with these keys:

- `mysql-root-password`
- `mysql-password`
- `mysql-replication-password`

The Secret is intentionally not stored in Git or rendered by the Helm charts.
Create it through a protected environment/bootstrap process using the actual
rotated values. Never commit the values or place them in a sample file.

Jenkins authenticates to Yandex Cloud through the preconfigured `yc` profile
in its container. The current portfolio setup uses the service account's
`cloud-registry.artifacts.pusher` and `k8s.cluster-api.admin` roles.

## Jenkins image includes:

The Jenkins agent must also include `curl` for the post-deployment smoke test.

kubectl 1.35.1
Helm 4.2.4
Helmfile 1.7.4
Docker CLI 29.7.2
Buildx 0.36.1
