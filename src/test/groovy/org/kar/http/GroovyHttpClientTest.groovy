package org.kar.http

import groovy.util.slurpersupport.GPathResult
import groovyx.net.http.HTTPBuilder
import net.sf.json.JSONObject
import org.apache.http.client.HttpClient
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.DefaultHttpClient
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.http.HttpServletResponse

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.ContentType.TEXT
/**
 * Testing different ways Groovy can help to interact with HTTP as a client.
 */
class GroovyHttpClientTest extends Specification {

    private static final String HELLO_WORLD_HTML = '''\
<html>
  <body>
    <p>hello world</p>
  </body>
</html>'''

    private static final String POST_RESPONSE = 'Successfully posted [arg:[foo]] with method POST'

    static int httpPort
    static String appName

    @Shared
    static HTTPBuilder http

    def setupSpec() {
        httpPort = System.properties['httpPort'] as int
        appName = System.properties['appName']
        http = new HTTPBuilder(makeURL(''))
    }

    def "from a String to an HTTP GET"() {
        when:
        String html = makeURL('helloWorld.groovy').toURL().text

        then:
        html == HELLO_WORLD_HTML
    }

    def "Java version to read from URL"() {
        when:
        URL oracle = new URL(makeURL('helloWorld.groovy'));
        URLConnection urlConnection = oracle.openConnection();
        BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        StringBuffer response = new StringBuffer();
        String inputLine;
        while ((inputLine = reader.readLine()) != null) {
            response.append(inputLine);
            response.append("\n");
        }
        reader.close();

        then:
        response.toString().trim() == HELLO_WORLD_HTML
    }

    @Unroll("The url #url should throw an exception of type #exception")
    def "exceptions can be thrown converting a String to URL and accessing the text"() {
        when:
        String html = url.toURL().text

        then:
        def e = thrown(exception)

        where:
        url                          | exception
        'htp://foo.com'              | MalformedURLException
        'http://google.com/notThere' | FileNotFoundException
    }

    def "from a String to GET with a Reader"() {
        when:
        String html
        makeURL('helloWorld.groovy').toURL().withReader { Reader reader ->
            html = reader.text
        }

        then:
        html == HELLO_WORLD_HTML
    }

    def "from a String to URLConnection with error handling"() {
        when:
        String html
        final HttpURLConnection connection = makeURL('notThere.groovy').toURL().openConnection()
        connection.inputStream.withReader { Reader reader ->
            html = reader.text
        }

        then:
        connection.responseCode == HttpServletResponse.SC_NOT_FOUND
        def e = thrown(FileNotFoundException)
    }

    def "POST from a URLConnection"() {
        when:
        final HttpURLConnection connection = makeURL('post.groovy').toURL().openConnection()
        connection.setDoOutput(true)
        connection.outputStream.withWriter { Writer writer ->
            writer << "arg=foo"
        }

        String response = connection.inputStream.withReader { Reader reader -> reader.text }

        then:
        connection.responseCode == HttpServletResponse.SC_OK
        response == POST_RESPONSE
    }

    def "Java version of POST from a URLConnection"() {
        when:
        URL url = new URL(makeURL('post.groovy'));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
        out.write("arg=foo");
        out.close();

        BufferedReader result = new BufferedReader(
                new InputStreamReader(connection.getInputStream()));
        StringBuffer response = new StringBuffer();
        String decodedString;
        while ((decodedString = result.readLine()) != null) {
            response.append(decodedString);
        }
        result.close();

        then:
        connection.responseCode == HttpServletResponse.SC_OK
        response.toString() == POST_RESPONSE
    }

    def "GET with HTTPBuilder"() {
        when:
        def (html, responseStatus) = http.get(path: 'helloWorld.groovy', contentType: TEXT) { resp, reader ->
            [reader.text, resp.status]
        }

        then:
        responseStatus == HttpServletResponse.SC_OK
        html == HELLO_WORLD_HTML
    }

    def "GET with HTTPBuilder and automatic parsing"() {
        when:
        def (html, responseStatus) = http.get(path: 'helloWorld.groovy') { resp, reader ->
            [reader, resp.status]
        }

        then:
        responseStatus == HttpServletResponse.SC_OK
        html instanceof GPathResult
        html.BODY.P.text() == 'hello world'
    }

    def "GET with HTTPBuilder and automatic JSON parsing"() {
        when:
        def (json, responseStatus) = http.get(path: 'indexJson.groovy', contentType: JSON) { resp, reader ->
            [reader, resp.status]
        }

        then:
        responseStatus == HttpServletResponse.SC_OK
        json instanceof JSONObject
        json.html.body.p == 'hello world'
    }

    def "GET with HTTPBuilder and error handling"() {
        when:
        int responseStatus
        http.handler.failure = { resp ->
            responseStatus = resp.status
        }
        http.get(path: 'notThere.groovy', contentType: TEXT) { resp, reader ->
            throw new IllegalStateException('should not be executed')
        }

        then:
        responseStatus == HttpServletResponse.SC_NOT_FOUND
    }

    def "POST with HTTPBuilder"() {
        when:
        String response
        int responseStatus

        http.post(path: 'post.groovy', body: [arg: 'foo']) { resp, reader ->
            responseStatus = resp.status
            response = reader.text()
        }

        then:
        responseStatus == HttpServletResponse.SC_OK
        response == POST_RESPONSE
    }

    def "POST reverse example"() {
        when:
        String response
        int responseStatus

        final String foo = 'foo bar'
        http.post(path: 'reverse.groovy', body: [string: foo]) { resp, reader ->
            responseStatus = resp.status
            response = reader.text()
        }

        then:
        responseStatus == HttpServletResponse.SC_OK
        response == foo.reverse()
    }

    def "HttpClient example in Java"() {
        when:
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(makeURL("helloWorld.groovy"));
        ResponseHandler<String> responseHandler = new BasicResponseHandler();
        String responseBody = httpclient.execute(httpget, responseHandler);

        then:
        responseBody == HELLO_WORLD_HTML
    }

    //less verbose version of HttpClient example in Java
    def "HttpClient example in Java as a one-liner"() {
        when:
        String response = new DefaultHttpClient().execute(new HttpGet(makeURL("helloWorld.groovy")),
                new BasicResponseHandler())

        then:
        response == HELLO_WORLD_HTML
    }

    private static String makeURL(String page) {
        "http://localhost:$httpPort/$appName/$page"
    }
}