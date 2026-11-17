import type {NextConfig} from 'next';
const config:NextConfig={async rewrites(){const upstream=process.env.API_URL||'http://localhost:8088';return ['api','oauth2','login'].map(path=>({source:`/${path}/:path*`,destination:`${upstream}/${path}/:path*`})).concat([{source:'/logout',destination:upstream+'/logout'}])}};
export default config;
