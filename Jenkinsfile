pipeline {
    agent any

    tools {
        gradle 'gradle-8.12'
    }

    environment {
        APP_IMAGE = 'cr.yandex/crpm5u802b9d7cp3853s/gradle-app'
        HELM_REPO_URL = 'git@github.com:morgenvor/java-mysql-chart.git'
        HELM_REPO_BRANCH = 'main'
        K8S_CLUSTER = 'k8s-cluster'
        K8S_NAMESPACE = 'default'
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

        stage('Build Image') {
            steps {
                script {
                    echo "Building image: ${env.APP_IMAGE}:${env.IMAGE_TAG}"
                    sh "docker build --provenance=false -t ${env.APP_IMAGE}:${env.IMAGE_TAG} ."
                    sh "docker push ${env.APP_IMAGE}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Fetch Helmfile') {
            steps {
                dir('java-mysql-chart') {
                    deleteDir()
                    sshagent(credentials: ['github-ssh-key']) {
                        sh "git clone -b ${env.HELM_REPO_BRANCH} ${env.HELM_REPO_URL} ."
                    }
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

                            dir('java-mysql-chart') {
                                sh 'helmfile lint --skip-deps'
                                sh 'helmfile template --skip-deps >/tmp/java-mysql-chart-rendered.yaml'
                                sh 'helmfile apply --skip-deps --wait --timeout 300 --suppress-secrets'
                            }

                            sh 'kubectl rollout status deployment/myapp -n "$K8S_NAMESPACE" --timeout=180s'
                        }
                    } finally {
                        sh "rm -f '${kubeconfig}'"
                    }
                }
            }
        }
    }
}
