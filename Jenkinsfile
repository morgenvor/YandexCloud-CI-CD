pipeline {
    agent any

    tools {
        gradle 'gradle-8.12'
    }

    environment {
        APP_IMAGE = 'cr.yandex/crpm5u802b9d7cp3853s/gradle-app'
        K8S_CLUSTER = 'k8s-cluster'
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
                sh 'gradle clean build'
            }
        }

        stage('Build and push Image') {
            steps {
                script {
                    echo "Building image: ${env.APP_IMAGE}:${env.IMAGE_TAG}"
                    sh "docker build --provenance=false -t ${env.APP_IMAGE}:${env.IMAGE_TAG} ."
                    def pushOutput = sh(
                        script: """
#!/usr/bin/env bash
set -o pipefail
docker push ${env.APP_IMAGE}:${env.IMAGE_TAG} 2>&1 | tee docker-push.log
""",
                        returnStdout: true
                    ).trim()
                    def digestMatch = pushOutput =~ /digest:\s+(sha256:[0-9a-f]+)/
                    if (!digestMatch.find()) {
                        error "Docker registry did not return an image digest"
                    }
                    env.IMAGE_DIGEST = digestMatch.group(1)
                    sh 'rm -f docker-push.log'
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
                                port_forward_log="$WORKSPACE/port-forward.log"
                                smoke_response="$WORKSPACE/smoke-response.json"
                                kubectl port-forward service/myapp 18080:8080 -n "$K8S_NAMESPACE" > "$port_forward_log" 2>&1 &
                                port_forward_pid=$!
                                cleanup() {
                                    kill "$port_forward_pid" 2>/dev/null || true
                                    rm -f "$port_forward_log" "$smoke_response"
                                }
                                trap cleanup EXIT

                                curl --fail --silent --show-error \
                                    --retry 15 --retry-delay 2 --retry-connrefused \
                                    "http://127.0.0.1:18080/get-data" > "$smoke_response"
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
