package cn.yl.common.utils;

import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.springframework.http.MediaType;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * http 请求工具 添加依赖httpmime
 */
@SuppressWarnings("all")
public abstract class HttpUtil {
    /**
     * get请求 params传参
     *
     * @param url        请求路径
     * @param params     参数
     * @param headParams 请求头
     * @return
     */
    public static String doHttpGet(String url, Map<String, String> params, Map<String, String> headParams) {
        String result = null;
        //1.获取httpclient
        CloseableHttpClient httpClient = HttpClients.createDefault();
        //接口返回结果
        CloseableHttpResponse response = null;
        String paramStr = null;
        try {
            List<BasicNameValuePair> paramsList = new ArrayList<BasicNameValuePair>();

            for (String key : params.keySet()) {
                paramsList.add(new BasicNameValuePair(key, params.get(key)));
            }
            paramStr = EntityUtils.toString(new UrlEncodedFormEntity(paramsList));
            //拼接参数
            StringBuffer sb = new StringBuffer();
            sb.append(url);
            sb.append("?");
            sb.append(paramStr);

            //2.创建get请求
            HttpGet httpGet = new HttpGet(sb.toString());
            //3.设置请求和传输超时时间
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(2000).setConnectTimeout(2000).build();
            httpGet.setConfig(requestConfig);
            /*此处可以添加一些请求头信息，例如：
            httpGet.addHeader("content-type","text/xml");*/
            for (String head : headParams.keySet()) {
                httpGet.addHeader(head, headParams.get(head));
            }
            //4.提交参数
            response = httpClient.execute(httpGet);
            //5.得到响应信息
            int statusCode = response.getStatusLine().getStatusCode();
            //6.判断响应信息是否正确
            if (HttpStatus.SC_OK != statusCode) {
                //终止并抛出异常
                httpGet.abort();
                throw new RuntimeException("HttpClient,error status code :" + statusCode);
            }
            //7.转换成实体类
            HttpEntity entity = response.getEntity();
            if (null != entity) {
                result = EntityUtils.toString(entity);
            }
            EntityUtils.consume(entity);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //8.关闭所有资源连接
            if (null != response) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }


    /**
     * http post params传参
     *
     * @param url        请求路径
     * @param params     参数
     * @param headParams 请求头
     * @return
     */
    public static String doPost(String url, Map<String, String> params, Map<String, String> headParams) {
        String result = null;
        //1. 获取httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        try {
            //2. 创建post请求
            HttpPost httpPost = new HttpPost(url);

            //3.设置请求和传输超时时间
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(100000).setConnectTimeout(100000).build();
            httpPost.setConfig(requestConfig);

            //4.提交参数发送请求
            List<BasicNameValuePair> paramsList = new ArrayList<>();
            for (String key : params.keySet()) {
                paramsList.add(new BasicNameValuePair(key, params.get(key)));
            }
            UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(paramsList, HTTP.UTF_8);
            httpPost.setEntity(urlEncodedFormEntity);
            //设置请求头
            for (String head : headParams.keySet()) {
                httpPost.addHeader(head, headParams.get(head));
            }

            response = httpClient.execute(httpPost);

            //5.得到响应信息
            int statusCode = response.getStatusLine().getStatusCode();
            //6. 判断响应信息是否正确
            if (HttpStatus.SC_OK != statusCode) {
                //结束请求并抛出异常
                httpPost.abort();
                throw new RuntimeException("HttpClient,error status code :" + statusCode);
            }
            //7. 转换成实体类
            HttpEntity entity = response.getEntity();
            if (null != entity) {
                result = EntityUtils.toString(entity, "UTF-8");
            }
            EntityUtils.consume(entity);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //8. 关闭所有资源连接
            if (null != response) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }


    /**
     * http post body传参
     *
     * @param url        请求路径
     * @param params     参数
     * @param headParams 请求头
     * @return
     */
    public static String httpPost(String url, String params, Map<String, String> headParams) {
        return process(new HttpPost(url), params, headParams);
    }

    /**
     * http put body传参
     *
     * @param url        请求路径
     * @param params     参数
     * @param headParams 请求头
     * @return
     */
    public static String httpPut(String url, String params, Map<String, String> headParams) {
        return process(new HttpPut(url), params, headParams);
    }


    private static String process(HttpEntityEnclosingRequestBase requestBase, String params, Map<String, String> headParams) {
        String result = null;
        // 1. 获取httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        try {
            // 3. 设置请求和传输超时时间
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(100000).setConnectTimeout(100000).build();
            requestBase.setConfig(requestConfig);
            // 4. 提交参数发送请求
            requestBase.setEntity(new StringEntity(params, ContentType.create("application/json", "utf-8")));
            // 设置请求头
            if (headParams != null) {
                for (String head : headParams.keySet()) {
                    requestBase.addHeader(head, headParams.get(head));
                }
            }
            response = httpClient.execute(requestBase);
            // 5. 得到响应信息
            int statusCode = response.getStatusLine().getStatusCode();
            // 6. 判断响应信息是否正确
            if (HttpStatus.SC_OK != statusCode) {
                // 结束请求并抛出异常
                requestBase.abort();
                throw new RuntimeException("HttpClient,error status code :" + statusCode);
            }
            // 7. 转换成实体类
            HttpEntity entity = response.getEntity();
            if (null != entity) {
                result = EntityUtils.toString(entity, "UTF-8");
            }
            EntityUtils.consume(entity);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 8. 关闭所有资源连接
            if (null != response) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != httpClient) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    /**
     * http post body传参
     *
     * @param url  请求路径
     * @param file 请求文件
     * @return
     */
    public static String httpPost(String url, File file) {
        try {
            // 创建 HttpClient 实例
            CloseableHttpClient httpClient = HttpClients.custom()
                    .addInterceptorFirst(new HttpRequestInterceptor() {
                        //在这里加入拦截器，对文件请求的contentType做出处理
                        @Override
                        public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
                            if (request instanceof HttpEntityEnclosingRequest) {
                                HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) request;
                                HttpEntity entity = entityRequest.getEntity();
                                if (entity != null && entity.getContentType() != null && entity.getContentType().getValue().startsWith("multipart/form-data")) {
                                    String contentType = entity.getContentType().getValue();
                                    String accept = MediaType.ALL.toString();
                                    entityRequest.setHeader("x-tilake-app-key", "");
                                    entityRequest.setHeader("x-tilake-ca-timestamp", "");
                                    entityRequest.setHeader("x-tilake-ca-signature", "");
                                    entityRequest.setHeader("Conetent-Type", contentType);
                                    entityRequest.setHeader("Accept", accept);
                                }
                            }
                        }
                    })
                    .build();

            // 创建 HTTP POST 请求
            HttpPost httpPost = new HttpPost(url);
            // 设置请求体内容
            // 创建 MultipartEntityBuilder
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // 添加其他参数
            builder.addTextBody("id", "111");
            // 添加文件流参数
            builder.addBinaryBody("file", file, ContentType.DEFAULT_BINARY, file.getName());
            /*绑定文件参数，传入文件流和contenttype，此处也可以继续添加其他formdata参数*/
//            builder.addBinaryBody("file",is, ContentType.MULTIPART_FORM_DATA,fileName);
            // 设置请求实体内容
            HttpEntity entity = builder.build();
            httpPost.setEntity(entity);
            // 执行请求并获取响应
            HttpResponse response = httpClient.execute(httpPost);
            // 解析响应
            HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity);
            // 处理响应结果
            System.out.println(responseBody);
            // 关闭 HttpClient
            httpClient.close();
            return responseBody;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
