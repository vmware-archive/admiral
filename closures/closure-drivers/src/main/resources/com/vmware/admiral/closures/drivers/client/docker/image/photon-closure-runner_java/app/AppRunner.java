import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

public class AppRunner {

    public static final String SRC_DIR = "./user_scripts";
    public static final String SRC_REQ_FILE = "requirements.txt";
    public static final String TRUSTED_CERTS = "/app/trust.pem";
    public static final String SRC_FILE_ZIP = "script.zip";
    public static final String SOURCE_URL = "sourceURL";
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PATCH = "PATCH";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String TOKEN = System.getenv("TOKEN");
    public static final String CLOSURE_URI = System.getenv("TASK_URI");
    public static final int BUFFER_SIZE = 10 * 1024;

    public static void main(String[] args) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date();
        String currentTime = dateFormat.format(date);
        System.out.format("Script run started at: %s%n", currentTime);

        AppRunner closure = new AppRunner();
        closure.proceedWithClosureExecution();
    }

    public void saveSourceInFile(JsonObject closureDescription, String moduleName) {
        BufferedWriter bufferedWriter = null;
        try {
            if (!new File(SRC_DIR).isDirectory()) {
                Files.createDirectories(Paths.get(SRC_DIR));
            }
            FileWriter writer = new FileWriter(SRC_DIR + File.separator + moduleName + ".java");
            bufferedWriter = new BufferedWriter(writer);
            bufferedWriter.write(closureDescription.get("source").getAsString());
        } catch (Exception e) {
            System.err.println("ERROR: Unable to save source file:");
            e.printStackTrace();
        } finally {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void patchResult(String outputs, String closureSemaphore) throws Exception {
        String state = "FINISHED";
        String data = String.format("{\"state\": %s, \"closureSemaphore\": %s, \"outputs\": %s}", state, closureSemaphore, outputs);

        HttpClient client = HttpClientBuilder.create().build();
        HttpPatch patch = new HttpPatch(CLOSURE_URI);
        setHeaders(patch);
        patch.setEntity(new StringEntity(data));
        HttpResponse response = client.execute(patch);

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 200) {
            System.out.println("Script run state: " + state);
        } else {
            String message = "Unable to patch result for closure from URI: " + CLOSURE_URI + " Status code: " + statusCode;
            patchFailure(closureSemaphore, new Exception(message));
        }
    }

    public static void setHeaders (HttpRequest request) {
        request.setHeader("Content-type", "application/json");
        request.setHeader("Accept", "application/json");
        request.setHeader("x-xenon-auth-token", TOKEN);
    }

    public static class Context {
        private String closureUri;
        private String closureSemaphore;
        private String inputs;
        private String outputs;

        public Context(String closureUri, String closureSemaphore, String inputs) {
            this.closureUri = closureUri;
            this.closureSemaphore = closureSemaphore;
            this.inputs = inputs;
            this.outputs = "{}";
        }

        public String readResponse (InputStream response) throws IOException {
            BufferedReader reader = null;
            StringBuffer result = null;
            try {
                reader = new BufferedReader(new InputStreamReader(response));
                result = new StringBuffer();
                String line = "";
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                reader.close();
            }
            return result.toString();
        }

        public String executeRequest(HttpRequest request, HttpClient client) {
            setHeaders(request);
            String resp = null;
            try {
                HttpResponse response = client.execute((HttpUriRequest) request);
                resp = readResponse(response.getEntity().getContent());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return resp;
        }

        public String executeRequestWithBody(HttpEntityEnclosingRequestBase request, HttpClient client, String body) {
            setHeaders(request);
            try {
                request.setEntity(new StringEntity(body));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            String resp = null;
            try {
                HttpResponse response = client.execute((HttpUriRequest) request);
                resp = readResponse(response.getEntity().getContent());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return resp;
        }


        public void executeDelegate (String link, String operation, String body, String handler) throws Exception {
            String op = operation.toUpperCase();
            String targetUri = buildClosureDescriptionUri(link);
            HttpClient client = HttpClientBuilder.create().build();
            String resp = null;
            switch (op) {
            case GET: HttpGet get = new HttpGet(targetUri);
                resp = executeRequest(get, client);
                break;
            case POST: HttpPost post = new HttpPost(targetUri);
                resp = executeRequestWithBody(post, client, body);
                break;
            case PATCH: HttpPatch patch = new HttpPatch(targetUri);
                resp = executeRequestWithBody(patch, client, body);
                break;
            case PUT: HttpPut put = new HttpPut(targetUri);
                resp = executeRequestWithBody(put, client, body);
                break;
            case DELETE: HttpDelete delete = new HttpDelete(targetUri);
                resp = executeRequest(delete, client);
                break;
            default: System.out.println("Unsupported operation on context.executeDelegate(): " + operation);
                patchFailure(this.closureSemaphore, new Exception("Unsupported operation: " + operation));
                break;
            }

            if (handler != null) {
                System.out.println(resp);
            }
        }
    }

    public void executeSavedSource(String inputs, String closureSemaphore, String moduleName, String handlerName) throws Exception {
        System.out.println("Script run logs:");
        System.out.println("*******************");
        try {
            Path path = Paths.get("");
            Path currentDir = path.toAbsolutePath();
            Path filePath = currentDir.resolve(SRC_DIR);

            Context context = new Context(CLOSURE_URI, closureSemaphore, inputs);
            runProcess("javac user_scripts/index.java", closureSemaphore);
            runProcess("java -cp ./user_scripts " + handlerName, closureSemaphore);
            System.out.println("*******************");
            patchResult(context.outputs, closureSemaphore);
        } catch (Exception ex) {
            System.out.println("*******************");
            System.out.println("Script run failed with: " + ex);
            patchFailure(closureSemaphore, ex);
            System.exit(1);
        } finally {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            Date date = new Date();
            String currentTime = dateFormat.format(date);
            System.out.format("Script run completed at: %s%n", currentTime);
        }
    }

    public void printOutput (InputStream input) {
        String line;
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        try {
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void runProcess (String command, String closureSemaphore) throws Exception {
        try {
            Process process = Runtime.getRuntime().exec(command);
            printOutput(process.getInputStream());
            process.waitFor();
            if (process.exitValue() != 0) {
                System.out.println("*******************");
                System.out.println("Script run failed with: ");
                printOutput(process.getErrorStream());
                patchFailure(closureSemaphore, new Exception(process.getErrorStream().toString()));
                System.exit(1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String buildClosureDescriptionUri(String closureDescLink) {
        String pattern = "/resources/closures/";
        String uriHead = CLOSURE_URI.split(pattern)[0];
        return uriHead + closureDescLink;
    }

    public void extractFile(ZipInputStream zipInput, String filePath) {
        BufferedOutputStream outputStream = null;
        try {
            outputStream = new BufferedOutputStream(
                    new FileOutputStream(filePath));
            byte[] bytesInput = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = zipInput.read(bytesInput)) != -1) {
                outputStream.write(bytesInput, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public HttpURLConnection getContentOfZipFile(String sourceUrl) {
        FileOutputStream output = null;
        InputStream input = null;
        HttpURLConnection connection = null;
        try {
            if (!new File(SRC_DIR).isDirectory()) {
                Files.createDirectories(Paths.get(SRC_DIR));
            }
            URL sourceURL = new URL(sourceUrl);
            connection = (HttpURLConnection) sourceURL.openConnection();
            connection.setRequestMethod(GET);
            input = connection.getInputStream();
            output = new FileOutputStream(SRC_FILE_ZIP);
            byte[] buffer = new byte[2048];
            int n = input.read(buffer);
            while (n >= 0) {
                output.write(buffer, 0, n);
                n = input.read(buffer);
            }
            output.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.close();
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return connection;
    }

    public void downloadAndSaveSource(String sourceUrl, String moduleName) {
        BufferedWriter bufferedWriter = null;
        try {
            HttpURLConnection connection = getContentOfZipFile(sourceUrl);
            int sourceUrlResponse = connection.getResponseCode();
            String contentType = connection.getHeaderField("content-type");
            if (sourceUrlResponse != 200) {
                String message = "Unable to fetch script source from: " + sourceUrl;
                throw new Exception(message);
            }
            ZipInputStream zipInput = null;
            try {
                if (contentType.equals("application/zip") || contentType
                        .equals("application/octet-stream")) {
                    System.out.println("Processing ZIP source file...");
                    zipInput = new ZipInputStream(new FileInputStream(SRC_FILE_ZIP),
                            Charset
                                    .forName("ISO-8859-1"));
                    ZipEntry entry = zipInput.getNextEntry();
                    while (entry != null) {
                        String filePath = SRC_DIR + File.separator + moduleName + ".java";
                        if (!entry.isDirectory()) {
                            extractFile(zipInput, filePath);
                        } else {
                            File directory = new File(filePath);
                            Files.createDirectories(Paths.get(directory.toString()));
                        }
                        zipInput.closeEntry();
                        entry = zipInput.getNextEntry();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (zipInput != null) {
                    zipInput.close();
                    Path path = Paths.get(SRC_FILE_ZIP);
                    Files.deleteIfExists(path);
                }
            }

        } catch (Exception e) {
            System.err.println("Exception while download and save script source:");
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String[] createEntryPoint(JsonObject closureDescription) {
        String handlerName = closureDescription.get("name").getAsString();
        String[] handlerAndIndexNames = {"index", handlerName};
        if (closureDescription.has("entrypoint")) {
            String entryPoint = closureDescription.get("entrypoint").getAsString();
            if (!entryPoint.isEmpty()) {
                String[] entries = entryPoint.split(".", 1);
                return entries;
            } else {
                return  handlerAndIndexNames;
            }
        } else {
            System.out.println("Entrypoint is empty. Will use closure name for a handler name: " + handlerName);
            return handlerAndIndexNames;
        }
    }

    public HttpResponse getClosureContent(String closureDescUri) {
        HttpResponse response = null;
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpGet get = new HttpGet(closureDescUri);
            setHeaders(get);
            response = client.execute(get);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }

    public String getResponseContent(HttpResponse response) {
        BufferedReader reader = null;
        StringBuffer responseContent = null;
        try {
            reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            responseContent = new StringBuffer();
            String line = "";
            while ((line = reader.readLine()) != null) {
                responseContent.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return responseContent.toString();
    }

    public void proceedWithClosureDescription(String closureDescUri, String inputs, String closureSemaphore) throws Exception {
        HttpResponse response = getClosureContent(closureDescUri);
        String responseContent = getResponseContent(response);
        int closureDescResponse = response.getStatusLine().getStatusCode();

        if (closureDescResponse == 200) {
            Gson gson = new Gson();
            JsonObject closureDescription = gson.fromJson(responseContent.toString(), JsonObject.class);
            String[] moduleAndIndexNames = createEntryPoint(closureDescription);
            String moduleName = moduleAndIndexNames[0];
            String handlerName = moduleAndIndexNames[1];

            if (closureDescription.has(SOURCE_URL)) {
                String sourceUrl = closureDescription.get(SOURCE_URL).getAsString();
                if (!sourceUrl.isEmpty()) {
                    downloadAndSaveSource(sourceUrl, moduleName);
                } else {
                    saveSourceInFile(closureDescription, moduleName);
                }
            } else {
                saveSourceInFile(closureDescription, moduleName);
            }
            executeSavedSource(inputs, closureSemaphore, moduleName, handlerName);
        } else {
            String message = "Unable to get closure description from URI: " + CLOSURE_URI + " Reason: " + closureDescResponse;
            throw new Exception(message);
        }
    }

    public void patchClosureStarted(String closureSemaphore) throws Exception {
        String state = "STARTED";
        String data = String
                .format("{\"state\": %s, \"closureSemaphore\": %s}", state, closureSemaphore);

        HttpClient client = HttpClientBuilder.create().build();
        HttpPatch patch = new HttpPatch(CLOSURE_URI);
        setHeaders(patch);
        patch.setEntity(new StringEntity(data));
        HttpResponse response = client.execute(patch);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            String message = "Unable to start closure from URI: " + CLOSURE_URI + " Status code: " + statusCode;
            throw new Exception(message);
        }
    }

    public boolean isBlank (String str) {
        str = str.trim();
        return str.isEmpty() || str == null;
    }

    public void proceedWithClosureExecution () throws Exception {
        if (isBlank(CLOSURE_URI)) {
            System.out.println("TASK_URI environment variable is not set. Aborting...");
            return;
        }

        HttpResponse response = getClosureContent(CLOSURE_URI);
        String responseContent = getResponseContent(response);
        int closureResponse = response.getStatusLine().getStatusCode();

        String closureInputs;
        if (closureResponse == 200) {
            Gson gson = new Gson();
            JsonObject closureData = gson.fromJson(responseContent.toString(), JsonObject.class);
            String closureSemaphore = closureData.get("closureSemaphore").getAsString();

            patchClosureStarted(closureSemaphore);

            if (closureData.get("inputs").toString().equals("{}")) {
                closureInputs = "{}";
            } else {
                closureInputs = closureData.get("inputs").toString();
            }

            String closureDescLink = closureData.get("descriptionLink").getAsString();
            String closureDescUri = buildClosureDescriptionUri(closureDescLink);
            proceedWithClosureDescription(closureDescUri, closureInputs, closureSemaphore);
        } else {
            String message = "Unable to get closure data from URI: " + CLOSURE_URI + " Reason: " + closureResponse;
            throw new Exception(message);
        }
    }

    public static void patchFailure(String closureSemaphore, Exception error)  throws Exception{
        String state = "FAILED";
        String data;
        if (closureSemaphore == null) {
            data = String.format("{\"state\": %s, \"errorMsg\": %s}", state, error);
        } else {
            data = String.format("{\"state\": %s,\"closureSemaphore\": %s, \"errorMsg\": %s}", state, closureSemaphore, error);
        }

        HttpClient client = HttpClientBuilder.create().build();
        HttpPatch patch = new HttpPatch(CLOSURE_URI);
        setHeaders(patch);
        patch.setEntity(new StringEntity(data));
        HttpResponse response = client.execute(patch);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 200) {
            System.out.println("Script run state: " + state);
        } else {
            String message = "Unable to patch failure closure from URI: " + CLOSURE_URI + " Status code: " + statusCode;
            throw new Exception(message);
        }
    }
}