/*
 * Copyright (c) 2010-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.component.prism;

import java.util.List;

import org.apache.commons.lang.Validate;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.model.PropertyWrapperFromContainerValueWrapperModel;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.AllFilter;
import com.evolveum.midpoint.prism.query.AndFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.TypeFilter;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.input.ExpressionValuePanel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.dto.StandardLoggerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AppenderConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ClassLoggerConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConstructionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LoggingComponentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LoggingConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableRowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NodeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * @author katkav
 */
public class PrismPropertyColumn<IW extends ItemWrapper> extends BasePanel<IW> {
	private static final long serialVersionUID = 1L;

	private static final Trace LOGGER = TraceManager.getTrace(PrismPropertyColumn.class);
    
    private boolean labelContainerVisible = true;
    private PageBase pageBase;
    
    public PrismPropertyColumn(String id, final IModel<IW> model, Form form, PageBase pageBase) {
        super(id, model);
        Validate.notNull(model, "no model");
        this.pageBase= pageBase;
        
        LOGGER.trace("Creating property panel for {}", model.getObject());
        
        if(model.getObject().getPath().removeIdentifiers().equivalent(new ItemPath(SystemConfigurationType.F_LOGGING, LoggingConfigurationType.F_CLASS_LOGGER, ClassLoggerConfigurationType.F_APPENDER))){
	        
        	((PropertyWrapper)model.getObject()).setPredefinedValues(WebComponentUtil.createAppenderChoices(pageBase));
        
        } else if(model.getObject().getPath().removeIdentifiers().equivalent(new ItemPath(SystemConfigurationType.F_LOGGING, LoggingConfigurationType.F_CLASS_LOGGER, ClassLoggerConfigurationType.F_PACKAGE))){
        	LookupTableType lookupTable = new LookupTableType();
	        List<LookupTableRowType> list = lookupTable.createRowList();
	        IModel<List<StandardLoggerType>> standardLoggers = WebComponentUtil.createReadonlyModelFromEnum(StandardLoggerType.class);
        	IModel<List<LoggingComponentType>> componentLoggers = WebComponentUtil.createReadonlyModelFromEnum(LoggingComponentType.class);
        	
        	for(StandardLoggerType standardLogger : standardLoggers.getObject()) {
        		LookupTableRowType row = new LookupTableRowType();
        		row.setKey(standardLogger.getValue());
        		row.setValue(standardLogger.getValue());
        		row.setLabel(new PolyStringType(createStringResource("StandardLoggerType." + standardLogger.name()).getString()));
        		list.add(row);
        	}
        	for(LoggingComponentType componentLogger : componentLoggers.getObject()) {
        		LookupTableRowType row = new LookupTableRowType();
        		row.setKey(componentLogger.value());
        		row.setValue(componentLogger.value());
        		row.setLabel(new PolyStringType(createStringResource("LoggingComponentType." + componentLogger.name()).getString()));
        		list.add(row);
        	}
	        ((PropertyWrapper)model.getObject()).setPredefinedValues(lookupTable);
        }
        
        setOutputMarkupId(true);
        add(new VisibleEnableBehaviour() {
			private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible() {
//            	IW propertyWrapper = model.getObject();
//            	boolean visible = propertyWrapper.isVisible();
//                LOGGER.trace("isVisible: {}: {}", propertyWrapper, visible);
//                return visible;
                return true;
            }

            @Override
            public boolean isEnabled() {
            	if(model.getObject() instanceof PropertyWrapper && model.getObject().getPath().isSuperPathOrEquivalent(new ItemPath(SystemConfigurationType.F_LOGGING, LoggingConfigurationType.F_CLASS_LOGGER))){
            		return ((PropertyWrapper)model.getObject()).getContainerValue().isSelected();
            	}
                return !model.getObject().isReadonly();
            }
        });

        initLayout(model, form);
    }

    private void initLayout(final IModel<IW> model, final Form form) {
        
        ListView<ValueWrapper> values = new ListView<ValueWrapper>("values",
            new PropertyModel<>(model, "values")) {
        	private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(final ListItem<ValueWrapper> item) {
                BasePanel panel = new PrismValuePanel("value", item.getModel(), createStringResource("smth"), form, getValueCssClass(), getInputCssClass());
                item.add(panel);
                item.add(AttributeModifier.append("class", createStyleClassModel(item.getModel())));

                item.add(new VisibleEnableBehaviour() {
                	private static final long serialVersionUID = 1L;

                    @Override
                    public boolean isVisible() {
                        return isVisibleValue(item.getModel());
                    }
                });
            }
        };
        values.add(new AttributeModifier("class", getValuesClass()));
        values.setReuseItems(true);
        add(values);
    }

    protected String getInputCssClass() {
        return"col-xs-10";
    }

    protected String getValuesClass() {
        return "col-md-6";
    }

    protected String getValueCssClass() {
        return "row";
    }

   
    protected IModel<String> createStyleClassModel(final IModel<ValueWrapper> value) {
        return new AbstractReadOnlyModel<String>() {
        	private static final long serialVersionUID = 1L;

            @Override
            public String getObject() {
                if (getIndexOfValue(value.getObject()) > 0) {
                    return "prism-value";
                }

                return null;
            }
        };
    }

    private int getIndexOfValue(ValueWrapper value) {
        ItemWrapper property = value.getItem();
        List<ValueWrapper> values = property.getValues();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equals(value)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isVisibleValue(IModel<ValueWrapper> model) {
        ValueWrapper value = model.getObject();
        return !ValueStatus.DELETED.equals(value.getStatus());
    }

    public boolean isLabelContainerVisible() {
        return labelContainerVisible;
    }

    public void setLabelContainerVisible(boolean labelContainerVisible) {
        this.labelContainerVisible = labelContainerVisible;
    }
}
