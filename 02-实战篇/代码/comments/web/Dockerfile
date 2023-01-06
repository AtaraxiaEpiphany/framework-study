FROM nginx:alpine
LABEL user=Ataraxia
COPY html /usr/share/nginx/html
COPY conf/nginx.conf /etc/nginx/nginx.conf
#COPY conf/nginx.conf /etc/nginx/conf.d/default.conf
RUN apk update && apk add bash
EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx","-g","daemon off;"]
