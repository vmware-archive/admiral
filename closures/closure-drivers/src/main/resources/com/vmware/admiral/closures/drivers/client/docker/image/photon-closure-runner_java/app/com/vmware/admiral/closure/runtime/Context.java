package com.vmware.admiral.closure.runtime;

/**
 * Context object passed to java runtime.
 */
public interface Context {

    /**
     * Returns inputs as json string
     *
     * @return string representation of json object
     */
    public String getInputs();

    /**
     * Retunrs outputs as json string
     *
     * @return string representation of json object
     */
    public String getOutputs();

    /**
     * Execute HTTP requests to admiral itself
     *
     * @param link resouce link
     * @param operation HTTP method
     * @param body body of the HTTP request
     * @param handler handler fucntion
     * @throws Exception thrown in case of error
     */
    public void execute(String link, String operation, String body, String handler) throws
            Exception;

}