/*
 * Copyright (c) 2010-2014 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.scripting.actions;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.impl.scripting.PipelineData;
import com.evolveum.midpoint.model.impl.scripting.ExecutionContext;
import com.evolveum.midpoint.model.api.ScriptExecutionException;
import com.evolveum.midpoint.model.api.PipelineItem;
import com.evolveum.midpoint.model.impl.scripting.helpers.OperationsHelper;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ActionExpressionType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author mederly
 */
@Component
public class DeleteExecutor extends BaseActionExecutor {

    private static final Trace LOGGER = TraceManager.getTrace(DeleteExecutor.class);

    private static final String NAME = "delete";

    @Autowired
    private OperationsHelper operationsHelper;

    @PostConstruct
    public void init() {
        scriptingExpressionEvaluator.registerActionExecutor(NAME, this);
    }

    @Override
    public PipelineData execute(ActionExpressionType expression, PipelineData input, ExecutionContext context, OperationResult globalResult) throws ScriptExecutionException {

        ModelExecuteOptions executionOptions = getOptions(expression, input, context, globalResult);
        boolean dryRun = getParamDryRun(expression, input, context, globalResult);

        for (PipelineItem item : input.getData()) {
            PrismValue value = item.getValue();
            OperationResult result = operationsHelper.createActionResult(item, this, context, globalResult);
            context.checkTaskStop();
            if (value instanceof PrismObjectValue) {
                @SuppressWarnings({"unchecked", "raw"})
                PrismObject<? extends ObjectType> prismObject = ((PrismObjectValue) value).asPrismObject();
                ObjectType objectType = prismObject.asObjectable();
                long started = operationsHelper.recordStart(context, objectType);
                Throwable exception = null;
                try {
                    operationsHelper.applyDelta(createDeleteDelta(objectType), executionOptions, dryRun, context, result);
                    operationsHelper.recordEnd(context, objectType, started, null);
                } catch (Throwable ex) {
                    operationsHelper.recordEnd(context, objectType, started, ex);
                    exception = processActionException(ex, NAME, value, context);
                }
                context.println((exception != null ? "Attempted to delete " : "Deleted ") + prismObject.toString() + optionsSuffix(executionOptions, dryRun) + exceptionSuffix(exception));
            } else {
                //noinspection ThrowableNotThrown
                processActionException(new ScriptExecutionException("Item is not a PrismObject"), NAME, value, context);
            }
            operationsHelper.trimAndCloneResult(result, globalResult, context);
        }
        return input;
    }

    private ObjectDelta<? extends ObjectType> createDeleteDelta(ObjectType objectType) {
        return prismContext.deltaFactory().object().createDeleteDelta(objectType.getClass(), objectType.getOid()
        );
    }

}
