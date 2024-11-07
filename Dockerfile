FROM jenkins/jenkins

LABEL maintainer="zefey <1076971426@qq.com>"

USER root

RUN curl -L -o rancher-cli.tar.gz https://rancher-mirror.rancher.cn/cli/v2.9.0/rancher-linux-amd64-v2.9.0.tar.gz

RUN tar xzf rancher-cli.tar.gz

RUN mv */rancher /usr/local/bin/

RUN chmod +x /usr/local/bin/rancher

RUN rancher --version

