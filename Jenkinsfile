pipeline {
    agent any
    
    tools {
        gradle 'gradle-8.12'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Increment Version') {
            steps {
                script {
                    echo 'Incrementing app version...'
                    sh "gradle updateVersion -Prelease.useAutomaticVersion=true"
                    def version = sh(script: "gradle properties -q | grep '^version:' | awk '{print \$2}'", returnStdout: true).trim()
            
                    env.IMAGE_NAME = "${version}-${BUILD_NUMBER}"
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
            environment {
                PATH = "/var/jenkins_home/yandex-cloud/bin:${env.PATH}"
            }
            steps {
                script {
                    echo "Building image: ${env.IMAGE_NAME}"
                    sh "docker build --provenance=false -t cr.yandex/crpm5u802b9d7cp3853s/gradle-app:${env.IMAGE_NAME} ."
                    sh "docker push cr.yandex/crpm5u802b9d7cp3853s/gradle-app:${env.IMAGE_NAME}"
                }
            }
        }
        stage('Deploy') {
            environment {
                K8S_NAME = 'java-gradle-app'
                PATH = "/var/jenkins_home/yandex-cloud/bin:${env.PATH}"
            }
            steps {
                script {
                    sh '''
                        yc managed-kubernetes cluster get-credentials \
                            k8s-cluster \
                            --external \
                            --kubeconfig ~/.kube/config \
                            --force

                        envsubst < k8s-pipeline/deployment.yaml | kubectl apply -f -
                        envsubst < k8s-pipeline/service.yaml | kubectl apply -f -
                    '''
                    sh "kubectl rollout status deployment/${K8S_NAME} --timeout=180s"
                }
            }
        }
        stage('Commit version update') {
            steps {
                script {
                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                        withCredentials([usernamePassword(credentialsId: 'githubapi', passwordVariable: 'PASS', usernameVariable: 'USER')]) { 
                        sh 'git config --local user.email "jenkins@example.com"'
                        sh 'git config --local user.name "jenkins"'

                        sh 'git add .'
                        sh 'git diff --cached --quiet || git commit -m "ci: version bump"'
                        sh "git push https://${USER}:${PASS}@github.com/morgenvor/dockerex.git HEAD:master"
                        }
                    }   
                }
            }
        }
    }
}
