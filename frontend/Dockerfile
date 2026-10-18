FROM node:24.22.0-bookworm-slim AS build
WORKDIR /workspace
RUN npm install --global npm@11.19.1
COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/.tools/npm-cache npm ci
COPY . .
RUN NG_CLI_ANALYTICS=false npm run build

FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/salary-management-ui/browser /usr/share/nginx/html
EXPOSE 80
