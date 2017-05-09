package com.vmware.admiral.closure;

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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
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
    public static final String TRUSTED_CERTS = "/app/trust.pem";
    public static final String SRC_FILE_ZIP = "script.zip";
    public static final String SOURCE_URL = "sourceURL";
    public static final String GET = "GET";
    public static final String TOKEN = System.getenv("TOKEN");
    public static final String CLOSURE_URI = System.getenv("TASK_URI");
    public static final int BUFFER_SIZE = 10 * 1024;

    public static void main(String[] args) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date();
        String currentTime = dateFormat.format(date);
        System.out.format("Script run started at: %s%n", currentTime);

        AppRunner closure = new AppRunner();
        try {
            closure.proceedWithClosureExecution();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public void patchResult(JsonObject outputs, String closureSemaphore) throws Exception {
        String state = "FINISHED";
        String data = String.format("{\"state\": %s, \"closureSemaphore\": %s, \"outputs\": %s}", state, closureSemaphore, outputs.toString());

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
            patchFailure(closureSemaphore, message);
        }
    }

    public static void setHeaders (HttpRequest request) {
        request.setHeader("Content-type", "application/json");
        request.setHeader("Accept", "application/json");
        request.setHeader("x-xenon-auth-token", TOKEN);
    }

    public void executeSavedSource(JsonObject inputs, String closureSemaphore, String handlerName) {
        System.out.println("Script run logs:");
        System.out.println("*******************");
        try {
            Path path = Paths.get("");
            Path currentDir = path.toAbsolutePath();
            Path filePath = currentDir.resolve(SRC_DIR);

            Context context = new Context(CLOSURE_URI, closureSemaphore, inputs);
            runProcess("javac -cp gson-2.6.2.jar:. -sourcepath user_scripts/ user_scripts/Test.java", closureSemaphore);
            File file = new File("user_scripts/");
            URL url = file.toURI().toURL();
            URL[] urls = new URL[]{url};
            ClassLoader classLoader = new URLClassLoader(urls);
            Class loadClass = classLoader.loadClass("Test");
            Constructor constructor = loadClass.getConstructor();
            Object object = constructor.newInstance();
            Method method = loadClass.getMethod(handlerName, Context.class);
            method.invoke(object, context);
            System.out.println("*******************");
            patchResult(context.outputs, closureSemaphore);
        } catch (Exception ex) {
            System.out.println("*******************");
            System.out.println("Script run failed with: " + ex);
            ex.printStackTrace();
            patchFailure(closureSemaphore, ex.toString());
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

    public void runProcess (String command, String closureSemaphore) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            printOutput(process.getInputStream());
            process.waitFor();
            if (process.exitValue() != 0) {
                System.out.println("*******************");
                System.out.println("Script run failed with: ");
                printOutput(process.getErrorStream());
                patchFailure(closureSemaphore, process.getErrorStream().toString());
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return connection;
    }

    public void downloadAndSaveSource(String sourceUrl, String closureSemaphore) {
        BufferedWriter bufferedWriter = null;
        try {
            HttpURLConnection connection = getContentOfZipFile(sourceUrl);
            int sourceUrlResponse = connection.getResponseCode();
            String contentType = connection.getHeaderField("content-type");
            if (sourceUrlResponse != 200) {
                String message = "Unable to fetch script source from: " + sourceUrl;
                patchFailure(closureSemaphore, message);
            }
            ZipInputStream zipInput = null;
            try {
                if (contentType.equals("application/zip") || contentType
                        .equals("application/octet-stream")) {
                    System.out.println("Processing ZIP source file...");
                    byte[] buffer = new byte[BUFFER_SIZE];
                    File src = new File(SRC_DIR);
                    if (!src.exists()) {
                        src.mkdir();
                    }
                    zipInput = new ZipInputStream(new FileInputStream(SRC_FILE_ZIP));
                    ZipEntry entry = zipInput.getNextEntry();

                    while(entry!=null){

                        String fileName = entry.getName();
                        File newFile = new File(SRC_DIR + File.separator + fileName);
                        new File(newFile.getParent()).mkdirs();

                        FileOutputStream fileOutputStream = new FileOutputStream(newFile);

                        int len;
                        while ((len = zipInput.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, len);
                        }

                        fileOutputStream.close();
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
        String[] handlerAndIndexNames = {"Test", handlerName};
        if (closureDescription.has("entrypoint")) {
            String entryPoint = closureDescription.get("entrypoint").getAsString();
            if (!entryPoint.isEmpty()) {
                String[] entries = entryPoint.split("", 1);
                return entries;
            } else {
                return  handlerAndIndexNames;
            }
        } else {
            System.out.println("Entry point is empty. Will use closure name for a handler name: " + handlerName);
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
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return responseContent.toString();
    }

    public void proceedWithClosureDescription(String closureDescUri, JsonObject inputs, String closureSemaphore) {
        HttpResponse response = getClosureContent(closureDescUri);
        String responseContent = getResponseContent(response);
        int closureDescResponse = response.getStatusLine().getStatusCode();
        String message = null;
        try {
            if (closureDescResponse == 200) {
                Gson gson = new Gson();
                JsonObject closureDescription = gson
                        .fromJson(responseContent.toString(), JsonObject.class);
                String[] moduleAndIndexNames = createEntryPoint(closureDescription);
                String moduleName = moduleAndIndexNames[0];
                String handlerName = moduleAndIndexNames[1];

                if (closureDescription.has(SOURCE_URL)) {
                    String sourceUrl = closureDescription.get(SOURCE_URL).getAsString();
                    if (!sourceUrl.isEmpty()) {
                        downloadAndSaveSource(sourceUrl, closureSemaphore);
                    } else {
                        saveSourceInFile(closureDescription, moduleName);
                    }
                } else {
                    saveSourceInFile(closureDescription, moduleName);
                }
                executeSavedSource(inputs, closureSemaphore, handlerName);
            } else {
                message = "Unable to get closure description from URI: " + CLOSURE_URI + " Reason: "
                        + closureDescResponse;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                patchFailure(closureSemaphore, message);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
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
            patchFailure(closureSemaphore, message);
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

        JsonObject closureInputs;
        String closureSemaphore = null;
        if (closureResponse == 200) {
            Gson gson = new Gson();
            JsonObject closureData = gson.fromJson(responseContent.toString(), JsonObject.class);
            closureSemaphore = closureData.get("closureSemaphore").getAsString();

            patchClosureStarted(closureSemaphore);

            closureInputs = (JsonObject) closureData.get("inputs");

            String closureDescLink = closureData.get("descriptionLink").getAsString();
            String closureDescUri = buildClosureDescriptionUri(closureDescLink);
            proceedWithClosureDescription(closureDescUri, closureInputs, closureSemaphore);
        } else {
            String message = "Unable to get closure data from URI: " + CLOSURE_URI + " Reason: " + closureResponse;
            patchFailure(closureSemaphore, message);
        }
    }

    public static void patchFailure(String closureSemaphore, String error) {
        String state = "FAILED";
        String data;
        if (closureSemaphore == null) {
            data = String.format("{\"state\": %s, \"errorMsg\": %s}", state, error);
        } else {
            data = String.format("{\"state\": %s,\"closureSemaphore\": %s, \"errorMsg\": %s}", state, closureSemaphore, error);
        }
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpPatch patch = new HttpPatch(CLOSURE_URI);
            setHeaders(patch);
            patch.setEntity(new StringEntity(data));
            HttpResponse response = client.execute(patch);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                System.out.println("Script run state: " + state);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}