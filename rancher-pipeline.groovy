// Jenkinsfile for Rancher 2.9 deployment

// 定义一个用于日志输出的函数
def logMessage(level, message) {
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
    def color_map = [
        'DEBUG': '\033[36m', // Cyan
        'INFO': '\033[32m',  // Green
        'WARN': '\033[33m',  // Yellow
        'ERROR': '\033[31m'  // Red
    ]
    def color = color_map[level] ?: '\033[0m'
    echo "${color}[${timestamp}] [${level}] ${message}\033[0m"
}

pipeline {
    agent any

    // 定义 Pipeline 参数
    parameters {
        // BuildId构建id
        string(
            defaultValue: '',
            description: 'BuildId构建id',
            name: 'BuildId'
        )
        // 构建项目名称
        string(
            defaultValue: '',
            description: '构建项目名称',
            name: 'ProjectName'
        )
        // deployment的名称
        string(
            defaultValue: '',
            description: 'deployment的名称',
            name: 'Deployment'
        )
        // container实际容器名称
        string(
            defaultValue: 'container-0',
            description: 'container实际容器名称',
            name: 'Container'
        )
        // 镜像
        string(
            defaultValue: '',
            description: '镜像',
            name: 'DockerImage'
        )

        // 触发构建的人
        string(
            defaultValue: '',
            description: 'RequestedFor',
            name: 'RequestedFor'
        )
        
        // 提交记录
        text(
            defaultValue: '',
            description: '提交记录',
            name: 'SourceVersionMessage'
        )
    }
    
    environment {
        RANCHER_URL = 'https://192.168.6.112:10443/v3'
        RANCHER_CONTEXT = 'c-m-rm6nk2dh:p-jqdhp'
        DEPLOYMENT = "${params.Deployment}"
        CONTAINER = "${params.Container}"
        DOCKER_IMAGE = "${params.DockerImage}"
        RANCHER_CLI_VERSION = 'v2.9.0'
        PATH = "/usr/local/bin:${env.PATH}"
        TEMP_RANCHER_TOKEN = 'token-2n7rn:mrvhq2ljkjbnl68tfvmwxz55rvlj2m2q5qpkg5nf2djhx7xtnxkfkg'
    }
    
    stages {
        stage('Initialize') {
            steps {
                script {
                    // 输出环境信息
                    logMessage 'INFO', "Starting pipeline execution"
                    logMessage 'DEBUG', """
                        Environment Details:
                        - BUILD_NUMBER: ${env.BUILD_NUMBER}
                        - WORKSPACE: ${env.WORKSPACE}
                        - JOB_NAME: ${env.JOB_NAME}
                        - NODE_NAME: ${env.NODE_NAME}
                    """
                    logMessage 'DEBUG', """
                        Environment Details:
                        - RANCHER_URL: ${RANCHER_URL}
                        - RANCHER_CONTEXT: ${RANCHER_CONTEXT}
                        - DEPLOYMENT: ${DEPLOYMENT}
                        - CONTAINER: ${CONTAINER}
                        - DOCKER_IMAGE: ${DOCKER_IMAGE}
                        - PATH: ${PATH}
                        - TEMP_RANCHER_TOKEN: ${TEMP_RANCHER_TOKEN}
                    """
                }
            }
        }

        // 外部已构建推送镜像
        // stage('Build') {
        //     steps {
        //         script {
        //             // 构建 Docker 镜像
        //             docker.build("${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}")
        //         }
        //     }
        // }
        
        // stage('Push') {
        //     steps {
        //         script {
        //             // 推送镜像到 registry
        //             docker.withRegistry("https://${DOCKER_REGISTRY}", 'registry-credentials') {
        //                 docker.image("${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}").push()
        //             }
        //         }
        //     }
        // }

        stage('Deploy to Rancher') {
            steps {
                // 使用 Rancher CLI 部署
                withCredentials([string(credentialsId: 'rancher-token', variable: 'RANCHER_TOKEN')]) {
                    sh """
                        rancher login ${RANCHER_URL} --token ${RANCHER_TOKEN} --context ${RANCHER_CONTEXT} --skip-verify
                        
                        # 更新工作负载镜像
                        rancher kubectl set image deployment/${DEPLOYMENT} \
                            ${CONTAINER}=${DOCKER_IMAGE} \
                            -n ych
                            
                        # 验证部署状态
                        rancher kubectl rollout status deployment/${DEPLOYMENT} -n ych
                    """
                }
            }
        }
        
        stage('Health Check') {
            steps {
                script {
                    // 等待服务就绪
                    sleep(30)
                    
                    // 执行健康检查
                    sh """
                        rancher kubectl get pods -l app=${DEPLOYMENT} -n ych \
                            -o jsonpath='{.items[*].status.containerStatuses[0].ready}'
                    """
                }
            }
        }
    }
    
    post {
        success {
            echo "部署成功完成"
        }
        failure {
            echo "部署失败"
            // 可以添加回滚逻辑
            sh """
                rancher kubectl rollout undo deployment/${DEPLOYMENT} -n ych
            """
        }
    }
}