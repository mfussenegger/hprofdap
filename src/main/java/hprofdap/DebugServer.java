

package hprofdap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.netbeans.lib.profiler.heap.Field;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.lib.profiler.heap.Type;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine.ObjectVisitor;
import org.netbeans.modules.profiler.oql.engine.api.OQLException;

public class DebugServer implements IDebugProtocolServer {

    private Heap heap;
    private OQLEngine engine;
    private IDebugProtocolClient client;
    private final InstanceCache instanceCache = new InstanceCache();

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        Capabilities capabilities = new Capabilities();
        return CompletableFuture.completedFuture(capabilities);
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> launch(Map<String, Object> args) {
        Object object = args.get("filepath");
        if (!(object instanceof String filepath)) {
            String message = "Expected `filepath` property in launch arguments";
            var error = new ResponseError(ResponseErrorCode.InvalidParams, message, null);
            throw new ResponseErrorException(error);
        }
        try {
            heap = HeapFactory.createHeap(new File(filepath));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        assert OQLEngine.isOQLSupported() : "OQL must be supported";
        engine = new OQLEngine(heap);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args) {
        engine = null;
        heap = null;
        instanceCache.clear();
        if (args.getTerminateDebuggee() && client != null) {
            client.terminated(new TerminatedEventArguments());
            System.exit(0);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
        raiseIfNotLaunched("scopes");
        Scope[] scopes = new Scope[0];
        ScopesResponse scopesResponse = new ScopesResponse();
        scopesResponse.setScopes(scopes);
        return CompletableFuture.completedFuture(scopesResponse);
    }

    @Override
    public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
        raiseIfNotLaunched("evaluate");
        String expression = args.getExpression();
        List<Instance> instances = new ArrayList<>();
        try {
            engine.executeQuery(expression, new ObjectVisitor() {

                @Override
                public boolean visit(Object arg0) {
                    if (!(arg0 instanceof Instance instance)) {
                        return false;
                    }
                    instances.add(instance);
                    return false;
                }
            });
        } catch (OQLException e) {
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, e.getMessage(), null));
        }
        EvaluateResponse evaluateResponse = new EvaluateResponse();
        if (instances.isEmpty()) {
            evaluateResponse.setResult("No result");
        } else if (instances.size() == 1) {
            Instance instance = instances.get(0);
            JavaClass javaClass = instance.getJavaClass();
            evaluateResponse.setResult(javaClass.getName());
            evaluateResponse.setVariablesReference(instanceCache.put(instance));
        } else {
            evaluateResponse.setResult("Collection [" + instances.size() + "]");
            evaluateResponse.setVariablesReference(instanceCache.put(instances));
        }
        return CompletableFuture.completedFuture(evaluateResponse);
    }

    private void raiseIfNotLaunched(String method) {
        if (engine == null) {
            String message = "Must launch session before calling " + method;
            var responseError = new ResponseError(ResponseErrorCode.InvalidRequest, message, null);
            throw new ResponseErrorException(responseError);
        }
    }

    private void setValues(Variable variable, Instance instance) {
        if (instance == null) {
            variable.setValue("null");
            return;
        }
        JavaClass javaClass = instance.getJavaClass();
        String name = javaClass.getName();
        switch (name) {
            case "java.lang.String" -> {
                variable.setType("String");
                PrimitiveArrayInstance arrayDump = (PrimitiveArrayInstance) instance.getValueOfField("value");
                List<?> values = arrayDump.getValues();
                byte[] bytes = new byte[values.size()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = Byte.valueOf((String) values.get(i));
                }
                variable.setValue(new String(bytes));
                break;
            }
            case "java.util.HashMap" -> {
                variable.setType("java.util.HashMap");
                Integer size = (Integer) instance.getValueOfField("size");
                variable.setValue("java.util.HashMap size=" + size);
                variable.setVariablesReference(instanceCache.put(instance));
            }
            default -> {
                if (instance instanceof PrimitiveArrayInstance arrayInstance) {
                    variable.setValue(name + " length=" + arrayInstance.getLength());
                } else {
                    variable.setValue(name);
                }
                variable.setVariablesReference(instanceCache.put(instance));
            }
        };
    }

    private Variable toVariable(Instance instance, Field field) {
        Variable variable = new Variable();
        Type type = field.getType();
        String name = field.getName();
        variable.setName(name);
        Object value = instance.getValueOfField(name);
        switch (value) {
            case null -> {
                variable.setType(type.getName());
                variable.setValue("null");
            }
            case Instance child -> {
                setValues(variable, child);
            }
            default -> {
                variable.setType(value.getClass().getSimpleName());
                variable.setValue(value.toString());
            }
        }
        return variable;
    }

    @SuppressWarnings("unchecked")
    private List<Variable> toVariables(Instance instance) {
        List<Variable> variables = new ArrayList<>();
        JavaClass javaClass = instance.getJavaClass();
        String className = javaClass.getName();
        List<?> fields = javaClass.getFields();
        if (fields.isEmpty()) {
            switch (instance) {
                case ObjectArrayInstance arrayInstance -> {
                    variables.addAll(toVariables((List<Instance>) arrayInstance.getValues()));
                }
                case PrimitiveArrayInstance arrayInstance -> {
                    List<?> values = arrayInstance.getValues();
                    String fmt = idxFormat(values.size());
                    String elementType = className.substring(0, className.length() - 3);
                    for (int i = 0; i < values.size(); i++) {
                        Object value = values.get(i);
                        Variable variable = new Variable();
                        variables.add(variable);
                        variable.setValue(value.toString());
                        variable.setName(String.format(Locale.ENGLISH, fmt, i));
                        variable.setType(elementType);
                    }
                }
                default -> {
                }
            }
        } else {
            for (var field : fields) {
                if (field instanceof Field f) {
                    variables.add(toVariable(instance, f));
                }
            }
        }
        return variables;
    }

    private static String idxFormat(int numInstances) {
        int numDigits = (int)(Math.floor(Math.log10(numInstances)) + 1);
        return "%0" + numDigits + "d";
    }

    private List<Variable> toVariables(List<Instance> instances) {
        List<Variable> variables = new ArrayList<>(Math.min(101, instances.size()));
        int i = 0;
        int numDigits = (int)(Math.floor(Math.log10(instances.size())) + 1);
        String fmt = "%0" + numDigits + "d";
        for (var instance : instances) {
            if (i > 100) {
                Variable remainder = new Variable();
                variables.add(remainder);
                remainder.setName("more");
                remainder.setValue("...");
                int varRef = instanceCache.put(instances.subList(i, instances.size()));
                remainder.setVariablesReference(varRef);
                break;
            }
            Variable variable = new Variable();
            variables.add(variable);
            variable.setName(String.format(Locale.ENGLISH, fmt, i));
            setValues(variable, instance);
            i++;
        }
        return variables;
    }


    @Override
    public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
        int variablesReference = args.getVariablesReference();
        Var var = instanceCache.get(variablesReference);
        var response = new VariablesResponse();
        response.setVariables(
            var.object()
                .bimap(this::toVariables, this::toVariables)
                .toArray(Variable[]::new)
        );
        return CompletableFuture.completedFuture(response);
    }

    public void setClientProxy(IDebugProtocolClient remoteProxy) {
        this.client = remoteProxy;
    }
}
