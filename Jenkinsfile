pipeline {
    agent any
    options { disableConcurrentBuilds() }
    parameters {
        string(name: 'CONFIG_ID', defaultValue: '1', description: '平台 RunConfig ID')
        string(name: 'BRANCH', defaultValue: 'dev', description: '测试代码分支')
        string(name: 'TARGET_ENV', defaultValue: 'local', description: '目标环境标识')
    }
    stages {
        stage('Deploy') {
            steps {
                sh 'docker compose up -d --build --force-recreate'
                // 端口探测等待就绪（dash 不支持 /dev/tcp，bash 可用）
                sh 'bash -c "until (exec 3<>/dev/tcp/host.docker.internal/8082) 2>/dev/null; do sleep 2; done"'
            }
        }
        stage('Test') {
            steps {
                withCredentials([string(credentialsId: 'platform-api-token', variable: 'PT_TOKEN')]) {
                    script {
                        // Groovy 插值：${params...}/${env...} 由 Groovy 求值；\$PT_TOKEN 转义留给 sh 展开
                        def rc = sh(script: """set +e
                            python3 /opt/platform_cli/platform_cli.py run \\
                              --config ${params.CONFIG_ID} --token "\$PT_TOKEN" \\
                              --api-url http://host.docker.internal:8000 \\
                              --wait --branch ${params.BRANCH} \\
                              --env BASE_URL=http://host.docker.internal:8082,TARGET_ENV=${params.TARGET_ENV},JENKINS_BUILD=${env.BUILD_NUMBER} \\
                              > /tmp/cli_out.txt 2>&1
                            echo \$?""", returnStdout: true).trim().toInteger()
                        def out = readFile('/tmp/cli_out.txt')
                        env.EXECUTION_ID = (out =~ /execution (\d+)/)[0][1]
                        if (rc != 0) { currentBuild.result = 'FAILURE' }   // 门禁
                    }
                }
            }
        }
        stage('Report') {
            steps {
                allure includeProperties: false, jdk: '', report: "/allure_reports/${env.EXECUTION_ID}"
            }
        }
    }
    post {
        failure { echo '构建失败：平台执行未通过，见 Allure 报告' }
    }
}
