# Java application Helmfile

This repository deploys the application, MySQL, and the optional phpMyAdmin
release through `helmfile.yaml.gotmpl`.
Charts installed in cluster but not mentioned in Helmfile:
- name: ingress-nginx
  url: https://kubernetes.github.io/ingress-nginx

## Required Secret

The target namespace must contain `mysql-secret` before `helmfile apply`.
The charts only reference this Secret; they do not create it and do not store
its values in Git.

Required keys:

- `mysql-root-password`
- `mysql-password`
- `mysql-replication-password`

Provision the Secret through a protected cluster bootstrap process, for
example with values supplied by a secure environment rather than committed
files:

```sh
kubectl create secret generic mysql-secret \
  --namespace default \
  --from-literal=mysql-root-password="$MYSQL_ROOT_PASSWORD" \
  --from-literal=mysql-password="$MYSQL_PASSWORD" \
  --from-literal=mysql-replication-password="$MYSQL_REPLICATION_PASSWORD"
```

Do not put the password variables or the generated Secret manifest in Git.
Rotate any credentials that were previously committed in repository history.

## Local rendering

Helmfile requires the application image tag explicitly:

```sh
APP_VERSION=dev helmfile lint --skip-deps
APP_VERSION=dev helmfile template --skip-deps
```

The Jenkins pipeline supplies the full Git commit SHA as `APP_VERSION` and
then runs `helmfile apply --wait`.
