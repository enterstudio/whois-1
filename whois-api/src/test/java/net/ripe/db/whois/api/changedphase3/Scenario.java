package net.ripe.db.whois.api.changedphase3;

import static com.google.common.base.Preconditions.checkArgument;
import static net.ripe.db.whois.api.changedphase3.Scenario.Req.NOT_APPLIC__;

public class Scenario {
    private final Mode mode;
    private final ObjectStatus preCond;
    private final Protocol protocol;
    private final Method method;
    private final Req req;
    private final Result result;
    private final ObjectStatus postCond;

    public Scenario(Mode mode, ObjectStatus preCond, Protocol protocol, Method method, Req req, Result result, ObjectStatus postCond) {
        checkArgument(mode != null);
        checkArgument(preCond != null);
        checkArgument(protocol != null);
        checkArgument(method != null);
        checkArgument(req != null);
        checkArgument(result != null);
        checkArgument(postCond != null);

        this.mode = mode;
        this.preCond = preCond;
        this.protocol = protocol;
        this.method = method;
        this.req = req;
        this.result = result;
        this.postCond = postCond;
    }

    public Mode getMode() {
        return mode;
    }

    public ObjectStatus getPreCond() {
        return preCond;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Method getMethod() {
        return method;
    }

    public Req getReq() {
        return req;
    }

    public Result getResult() {
        return result;
    }

    public ObjectStatus getPostCond() {
        return postCond;
    }

    public void run(final Context context) {
        //System.err.println("*** Start running test " + this);
        ScenarioRunner runner = RunnerProvider.getRunnerForProtocol(protocol, context);
        runner.before(this);
        switch (method) {
            case CREATE:
                runner.create(this);
                break;

            case MODIFY:
                runner.modify(this);
                break;

            case DELETE:
                runner.delete(this);
                break;

            case SEARCH:
                runner.search(this);
                break;

            case GET___:
                runner.get(this);
                break;

            case META__:
                runner.meta(this);
                break;

            case EVENT_:
                runner.event(this);
                break;
        }
        runner.after(this);
        //System.err.println("*** Done running test " + this);

    }

    public String toString() {
        return String.format("GIVEN( %s, %s ) WHEN( %s  %s, %s ) THEN( %s, %s )",
                getMode(), getPreCond(), getProtocol(), getMethod(), getReq(), getResult(), getPostCond());
    }

    public enum Mode {
        OLD_MODE,
        NEW_MODE
    }

    public enum ObjectStatus {
        OBJ_EXISTS_NO_CHANGED__,
        OBJ_EXISTS_WITH_CHANGED,
        OBJ_DOES_NOT_EXIST_____
    }

    public enum Protocol {
        REST___,
        TELNET_,
        SYNCUPD,
        MAILUPD,
        NRTM___
    }

    public enum Method {
        CREATE,
        MODIFY,
        DELETE,
        SEARCH,
        GET___,
        META__,
        EVENT_
    }

    public enum Req {
        WITH_CHANGED,
        NO_CHANGED__,
        NOT_APPLIC__
    }

    public enum Result {
        SUCCESS,
        FAILED
    }

    static class Builder {
        private Mode mode;
        private ObjectStatus preCond;
        private Protocol protocol;
        private Method method;
        private Req req;

        public static Builder given(final Mode mode, final ObjectStatus preCond) {
            Builder builder = new Builder();
            builder.mode = mode;
            builder.preCond = preCond;
            return builder;
        }

        public Builder when(final Protocol protocol, final Method method) {
            this.protocol = protocol;
            this.method = method;
            this.req = NOT_APPLIC__;
            return this;
        }

        public Builder when(final Protocol protocol, final Method method, final Req req) {
            this.protocol = protocol;
            this.method = method;
            this.req = req;
            return this;
        }

        public Scenario then(final Result result) {
            return new Scenario(
                    mode, preCond, protocol,
                    method, req,
                    result, preCond);
        }

        public Scenario then(final Result result, final ObjectStatus postCond) {
            return new Scenario(
                    mode, preCond, protocol,
                    method, req,
                    result, postCond);
        }

    }
}