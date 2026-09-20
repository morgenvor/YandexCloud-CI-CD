pipeline {
    agent any

    parameters {
        string(name: 'APP_IMAGE', defaultValue: 'cr.yandex/crpm5u802b9d7cp3853s/gradle-app', description: 'Yandex Container Registry image path, for example cr.yandex/<registry-id>/gradle-app')
        string(name: 'K8S_CLUSTER', defaultValue: 'k8s-cluster', description: 'Yandex Managed Kubernetes cluster name')
        string(name: 'SMOKE_TEST_URL', defaultValue: 'http://84.252.132.38', description: 'Public HTTP base URL for the /get-data smoke test')
    }

    environment {
        K8S_NAMESPACE = 'default'
        PATH = "/var/jenkins_home/yandex-cloud/bin:${env.PATH}"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Prepare Build') {
            steps {
                script {
                    if (!params.APP_IMAGE?.trim()) {
                        error 'APP_IMAGE must be set to the Yandex Container Registry image path'
                    }
                    if (!params.K8S_CLUSTER?.trim()) {
                        error 'K8S_CLUSTER must be set to the Yandex Managed Kubernetes cluster name'
                    }
                    if (!params.SMOKE_TEST_URL?.trim()) {
                        error 'SMOKE_TEST_URL must be set to the public HTTP base URL'
                    }
                    env.APP_IMAGE = params.APP_IMAGE.trim()
                    env.K8S_CLUSTER = params.K8S_CLUSTER.trim()
                    env.SMOKE_TEST_URL = params.SMOKE_TEST_URL.trim().replaceAll('/+$', '')
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()
                    echo "Building commit ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Build JAR') {
            steps {
                echo "Building jar..."
                sh './gradlew clean build'
            }
        }

        stage('Build and push Image') {
            steps {
                script {
                    echo "Building image: ${env.APP_IMAGE}:${env.IMAGE_TAG}"
                    sh "docker build --provenance=false -t ${env.APP_IMAGE}:${env.IMAGE_TAG} ."
                    sh "docker push ${env.APP_IMAGE}:${env.IMAGE_TAG}"

                    env.IMAGE_DIGEST = sh(
                        script: "docker inspect --format='{{index .RepoDigests 0}}' ${env.APP_IMAGE}:${env.IMAGE_TAG} | cut -d'@' -f2",
                        returnStdout: true
                    ).trim()

                    if (!env.IMAGE_DIGEST) {
                        error "Failed to retrieve image digest via docker inspect"
                    }

                    echo "Published image digest: ${env.IMAGE_DIGEST}"
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def kubeconfig = "${env.WORKSPACE}/kubeconfig"
                    try {
                        withEnv([
                            "KUBECONFIG=${kubeconfig}",
                            "APP_VERSION=${env.IMAGE_TAG}",
                            "APP_IMAGE_DIGEST=${env.IMAGE_DIGEST}"
                        ]) {
                            sh '''
                        yc managed-kubernetes cluster get-credentials \
                            "$K8S_CLUSTER" \
                            --external \
                            --kubeconfig "$KUBECONFIG" \
                            --force

                        kubectl get secret mysql-secret -n "$K8S_NAMESPACE" -o name
                            '''

                            dir('helm') {
                                sh 'helmfile lint --skip-deps'
                                sh '''
                                    set -eu
                                    rendered="$WORKSPACE/helmfile-rendered.yaml"
                                    trap 'rm -f "$rendered"' EXIT
                                    helmfile template --skip-deps --quiet > "$rendered"
                                    test -s "$rendered"
                                '''
                                sh 'helmfile apply --skip-deps --wait --timeout 300 --suppress-secrets'
                            }

                            sh 'kubectl rollout status deployment/myapp -n "$K8S_NAMESPACE" --timeout=180s'
                            sh 'kubectl rollout status deployment/phpmyadmin -n "$K8S_NAMESPACE" --timeout=180s'
                            sh 'kubectl rollout status statefulset/mysql-primary -n "$K8S_NAMESPACE" --timeout=300s'
                            sh 'kubectl rollout status statefulset/mysql-secondary -n "$K8S_NAMESPACE" --timeout=300s'
                            sh '''
                                set -eu
                                smoke_response="$WORKSPACE/smoke-response.json"
                                trap 'rm -f "$smoke_response"' EXIT

                                curl --fail --silent --show-error \
                                    --retry 15 \
                                    --retry-delay 2 \
                                    --retry-connrefused \
                                    --connect-timeout 5 \
                                    --max-time 30 \
                                    "${SMOKE_TEST_URL}/get-data" \
                                    > "$smoke_response"
                                test -s "$smoke_response"
                                grep -q 'Sarah' "$smoke_response"
                            '''
                        }
                    } finally {
                        sh "rm -f '${kubeconfig}'"
                    }
                }
            }
        }
    }
}
