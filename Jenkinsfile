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
                    sh "docker push ${env.APP_IMAGE}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def kubeconfig = "${env.WORKSPACE}/kubeconfig"
                    try {
                        withEnv(["KUBECONFIG=${kubeconfig}", "APP_VERSION=${env.IMAGE_TAG}"]) {
                            sh '''
                        yc managed-kubernetes cluster get-credentials \
                            "$K8S_CLUSTER" \
                            --external \
                            --kubeconfig "$KUBECONFIG" \
                            --force

                        kubectl get secret mysql-secret -n "$K8S_NAMESPACE" >/dev/null
                            '''

                            dir('helm') {
                                sh 'helmfile lint --skip-deps'
                                sh 'helmfile template --skip-deps >/tmp/java-mysql-public-rendered.yaml'
                                sh 'helmfile apply --skip-deps --suppress-secrets'
                            }

                            sh 'kubectl rollout status deployment/myapp -n "$K8S_NAMESPACE" --timeout=180s'
                            sh 'kubectl rollout status deployment/phpmyadmin -n "$K8S_NAMESPACE" --timeout=180s'
                            sh 'kubectl rollout status statefulset/mysql-primary -n "$K8S_NAMESPACE" --timeout=300s'
                            sh 'kubectl rollout status statefulset/mysql-secondary -n "$K8S_NAMESPACE" --timeout=300s'
                        }
                    } finally {
                        sh "rm -f '${kubeconfig}'"
                    }
                }
            }
        }
    }
}
