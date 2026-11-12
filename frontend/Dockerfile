# "stable" follows the current stable nginx line, which receives security fixes
FROM nginx:stable-alpine

# Apply OS security updates published after the base image was built
RUN apk upgrade --no-cache

COPY css/ /usr/share/nginx/html/css/
COPY js/  /usr/share/nginx/html/js/
COPY img/ /usr/share/nginx/html/img/
COPY *.html /usr/share/nginx/html/

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
