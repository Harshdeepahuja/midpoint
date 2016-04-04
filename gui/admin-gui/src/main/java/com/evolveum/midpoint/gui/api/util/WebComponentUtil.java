/*
 * Copyright (c) 2010-2016 Evolveum
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

package com.evolveum.midpoint.gui.api.util;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.match.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.ItemPathSegment;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.TaskCategory;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.TabbedPanel;
import com.evolveum.midpoint.web.component.data.BaseSortableDataProvider;
import com.evolveum.midpoint.web.component.data.Table;
import com.evolveum.midpoint.web.component.input.DropDownChoicePanel;
import com.evolveum.midpoint.web.component.objectdetails.AbstractObjectMainPanel;
import com.evolveum.midpoint.web.component.util.Selectable;
import com.evolveum.midpoint.web.page.PageDialog;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnBlurAjaxFormUpdatingBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnChangeAjaxFormUpdatingBehavior;
import com.evolveum.midpoint.web.page.admin.resources.PageResource;
import com.evolveum.midpoint.web.page.admin.roles.PageRole;
import com.evolveum.midpoint.web.page.admin.server.PageTaskEdit;
import com.evolveum.midpoint.web.page.admin.users.PageOrgUnit;
import com.evolveum.midpoint.web.page.admin.users.PageUser;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.wicket.*;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.authroles.authentication.AuthenticatedWebApplication;
import org.apache.wicket.authroles.authorization.strategies.role.Roles;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.util.visit.IVisitor;
import org.jetbrains.annotations.NotNull;
import org.joda.time.format.DateTimeFormat;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Utility class containing miscellaneous methods used mostly in Wicket components.
 * 
 * @author lazyman
 */
public final class WebComponentUtil {

	private static final Trace LOGGER = TraceManager.getTrace(WebComponentUtil.class);
	private static DatatypeFactory df = null;

	public static enum Channel {
		// TODO: move this to schema component
		LIVE_SYNC("http://midpoint.evolveum.com/xml/ns/public/provisioning/channels-3#liveSync"), 
		RECONCILIATION("http://midpoint.evolveum.com/xml/ns/public/provisioning/channels-3#reconciliation"), 
		DISCOVERY("http://midpoint.evolveum.com/xml/ns/public/provisioning/channels-3#discovery"), 
		IMPORT("http://midpoint.evolveum.com/xml/ns/public/provisioning/channels-3#import"), 
		USER("http://midpoint.evolveum.com/xml/ns/public/provisioning/channels-3#user"),
		WEB_SERVICE("http://midpoint.evolveum.com/xml/ns/public/provisioning/channels-3#webService");

		private String channel;

		Channel(String channel) {
			this.channel = channel;
		}

		public String getChannel() {
			return channel;
		}
	}

	static {
		try {
			df = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException dce) {
			throw new IllegalStateException("Exception while obtaining Datatype Factory instance", dce);
		}
	}

	private WebComponentUtil() {
	}

	public static boolean isAuthorized(String... action) {
		if (action == null || action.length == 0) {
			return true;
		}
		List<String> actions = Arrays.asList(action);
		return isAuthorized(actions);
	}

	public static boolean isAuthorized(Collection<String> actions) {
		if (actions == null || actions.isEmpty()) {
			return true;
		}
		Roles roles = new Roles(AuthorizationConstants.AUTZ_ALL_URL);
		roles.addAll(actions);
		if (((AuthenticatedWebApplication) AuthenticatedWebApplication.get()).hasAnyRole(roles)) {
			return true;
		}
		return false;
	}

	public static Integer safeLongToInteger(Long l) {
		if (l == null) {
			return null;
		}

		if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
			throw new IllegalArgumentException("Couldn't transform long '" + l + "' to int, too big or too small.");
		}

		return (int) l.longValue();
	}

	public static List<QName> createFocusTypeList() {
		List<QName> focusTypeList = new ArrayList<>();

		focusTypeList.add(UserType.COMPLEX_TYPE);
		focusTypeList.add(OrgType.COMPLEX_TYPE);
		focusTypeList.add(RoleType.COMPLEX_TYPE);

		return focusTypeList;
	}
	
	public static List<QName> createAssignableTypesList() {
		List<QName> focusTypeList = new ArrayList<>();

		focusTypeList.add(ResourceType.COMPLEX_TYPE);
		focusTypeList.add(OrgType.COMPLEX_TYPE);
		focusTypeList.add(RoleType.COMPLEX_TYPE);

		return focusTypeList;
	}

	public static <T extends Enum> IModel<String> createLocalizedModelForEnum(T value, Component comp) {
		String key = value != null ? value.getClass().getSimpleName() + "." + value.name() : "";
		return new StringResourceModel(key, comp, null);
	}

	public static <T extends Enum> IModel<List<T>> createReadonlyModelFromEnum(final Class<T> type) {
		return new AbstractReadOnlyModel<List<T>>() {

			@Override
			public List<T> getObject() {
				List<T> list = new ArrayList<T>();
				Collections.addAll(list, type.getEnumConstants());

				return list;
			}
		};
	}

	public static List<String> createTaskCategoryList() {
		List<String> categories = new ArrayList<>();

		// todo change to something better and add i18n
		// TaskManager manager = getTaskManager();
		// List<String> list = manager.getAllTaskCategories();
		// if (list != null) {
		// Collections.sort(list);
		// for (String item : list) {
		// if (item != TaskCategory.IMPORT_FROM_FILE && item !=
		// TaskCategory.WORKFLOW) {
		// categories.add(item);
		// }
		// }
		// }
		categories.add(TaskCategory.LIVE_SYNCHRONIZATION);
		categories.add(TaskCategory.RECONCILIATION);
		categories.add(TaskCategory.IMPORTING_ACCOUNTS);
		categories.add(TaskCategory.RECOMPUTATION);
		categories.add(TaskCategory.DEMO);
		// TODO: what about other categories?
		// categories.add(TaskCategory.ACCESS_CERTIFICATION);
		// categories.add(TaskCategory.BULK_ACTIONS);
		// categories.add(TaskCategory.CUSTOM);
		// categories.add(TaskCategory.EXECUTE_CHANGES);
		// categories.add(TaskCategory.IMPORT_FROM_FILE);
		// categories.add(TaskCategory.IMPORT_FROM_FILE);
		return categories;
	}

	public static ObjectReferenceType createObjectRef(String oid, String name, QName type) {
		ObjectReferenceType ort = new ObjectReferenceType();
		ort.setOid(oid);
		ort.setTargetName(createPolyFromOrigString(name));
		ort.setType(type);
		return ort;
	}

	// public static DropDownChoicePanel createActivationStatusPanel(String id,
	// final IModel<ActivationStatusType> model,
	// final Component component) {
	// return new DropDownChoicePanel(id, model,
	// WebMiscUtil.createReadonlyModelFromEnum(ActivationStatusType.class),
	// new IChoiceRenderer<ActivationStatusType>() {
	//
	// @Override
	// public Object getDisplayValue(ActivationStatusType object) {
	// return WebMiscUtil.createLocalizedModelForEnum(object,
	// component).getObject();
	// }
	//
	// @Override
	// public String getIdValue(ActivationStatusType object, int index) {
	// return Integer.toString(index);
	// }
	// }, true);
	// }

	public static <E extends Enum> DropDownChoicePanel createEnumPanel(Class clazz, String id, final IModel<E> model,
			final Component component) {
		// final Class clazz = model.getObject().getClass();
		final Object o = model.getObject();
		return new DropDownChoicePanel(id, model, WebComponentUtil.createReadonlyModelFromEnum(clazz),
				new IChoiceRenderer<E>() {

					@Override
					public E getObject(String id, IModel<? extends List<? extends E>> choices) {
						if (StringUtils.isBlank(id)) {
							return null;
						}
						return choices.getObject().get(Integer.parseInt(id));
					}

					@Override
					public Object getDisplayValue(E object) {
						return WebComponentUtil.createLocalizedModelForEnum(object, component).getObject();
					}

					@Override
					public String getIdValue(E object, int index) {
						return Integer.toString(index);
					}
				}, true);
	}

	public static DropDownChoicePanel createEnumPanel(final PrismPropertyDefinition def, String id, final IModel model,
			final Component component) {
		// final Class clazz = model.getObject().getClass();
		final Object o = model.getObject();

		final IModel<List<DisplayableValue>> enumModelValues = new AbstractReadOnlyModel<List<DisplayableValue>>() {
			@Override
			public List<DisplayableValue> getObject() {
				List<DisplayableValue> values = null;
				if (def.getAllowedValues() != null) {
					values = new ArrayList<>(def.getAllowedValues().size());
					for (Object v : def.getAllowedValues()) {
						if (v instanceof DisplayableValue) {
							values.add(((DisplayableValue) v));
						}
					}
				}
				return values;
			}

		};

		return new DropDownChoicePanel(id, model, enumModelValues, new IChoiceRenderer() {

			@Override
			public Object getObject(String id, IModel choices) {
				if (StringUtils.isBlank(id)) {
					return null;
				}
				return ((List) choices.getObject()).get(Integer.parseInt(id));
			}

			@Override
			public Object getDisplayValue(Object object) {
				if (object instanceof DisplayableValue) {
					return ((DisplayableValue) object).getLabel();
				}
				for (DisplayableValue v : enumModelValues.getObject()) {
					if (object.equals(v.getValue())) {
						return v.getLabel();
					}
				}
				return object;

			}

			@Override
			public String getIdValue(Object object, int index) {
				return String.valueOf(index);
				// for (DisplayableValue v : enumModelValues.getObject()){
				// if (object.equals(v.getValue())){
				// return v.getLabel();
				// }
				// }
				// return
				// object.getValue().toString();//Integer.toString(index);
			}

		}, true);
	}

	public static <T> TextField<T> createAjaxTextField(String id, IModel<T> model) {
		TextField<T> textField = new TextField<T>(id, model);
		textField.add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
		return textField;
	}

	public static CheckBox createAjaxCheckBox(String id, IModel<Boolean> model) {
		CheckBox checkBox = new CheckBox(id, model);
		checkBox.add(new EmptyOnChangeAjaxFormUpdatingBehavior());
		return checkBox;
	}

	public static String getName(ObjectType object) {
		if (object == null) {
			return null;
		}

		return getName(object.asPrismObject());
	}

	public static String getEffectiveName(ObjectType object, QName propertyName) {
		if (object == null) {
			return null;
		}

		return getEffectiveName(object.asPrismObject(), propertyName);
	}

	public static <O extends ObjectType> String getEffectiveName(PrismObject<O> object, QName propertyName) {
    	if (object == null) {
            return null;
        }
    	
    	PrismProperty prop = object.findProperty(propertyName);
    	
    	if (prop!= null && prop.getDefinition().getTypeName().equals(DOMUtil.XSD_STRING)){
    		return (String) prop.getRealValue();
    	}
    	
        PolyString name = getValue(object, ObjectType.F_NAME, PolyString.class);

        return name != null ? name.getOrig() : null;
    }


	public static String getName(ObjectReferenceType ref) {
		if (ref == null) {
			return null;
		}
		if (ref.getTargetName() != null) {
			return getOrigStringFromPoly(ref.getTargetName());
		}
		if (ref.asReferenceValue().getObject() != null) {
			return getName(ref.asReferenceValue().getObject());
		}
		return ref.getOid();
	}

	public static String getName(PrismObject object) {
		if (object == null) {
			return null;
		}
		PolyString name = getValue(object, ObjectType.F_NAME, PolyString.class);

		return name != null ? name.getOrig() : null;
	}

	public static String getIdentification(ObjectType object) {
		if (object == null) {
			return null;
		}
		return getName(object.asPrismObject()) + " (" + object.getOid() + ")";
	}

	public static PolyStringType createPolyFromOrigString(String str) {
		if (str == null) {
			return null;
		}

		PolyStringType poly = new PolyStringType();
		poly.setOrig(str);

		return poly;
	}

	public static String getOrigStringFromPoly(PolyString str) {
		return str != null ? str.getOrig() : null;
	}

	public static String getOrigStringFromPoly(PolyStringType str) {
		return str != null ? str.getOrig() : null;
	}

	public static <T> T getValue(PrismContainerValue object, QName propertyName, Class<T> type) {
		if (object == null) {
			return null;
		}

		PrismProperty property = object.findProperty(propertyName);
		if (property == null || property.isEmpty()) {
			return null;
		}

		return (T) property.getRealValue(type);
	}

	public static <T> T getContainerValue(PrismContainerValue object, QName containerName, Class<T> type) {
		if (object == null) {
			return null;
		}

		PrismContainer container = object.findContainer(containerName);
		if (container == null || container.isEmpty()) {
			return null;
		}

		PrismContainerValue containerValue = container.getValue();

		if (containerValue == null || containerValue.isEmpty()) {
			return null;
		}

		return (T) containerValue.getValue();
	}

	public static <T> T getValue(PrismContainer object, QName propertyName, Class<T> type) {
		if (object == null) {
			return null;
		}

		return getValue(object.getValue(), propertyName, type);
	}

	public static Locale getLocaleFromString(String localeString) {
		if (localeString == null) {
			return null;
		}
		localeString = localeString.trim();
		if (localeString.toLowerCase().equals("default")) {
			return Locale.getDefault();
		}

		// Extract language
		int languageIndex = localeString.indexOf('_');
		String language = null;
		if (languageIndex == -1) {
			// No further "_" so is "{language}" only
			return new Locale(localeString, "");
		} else {
			language = localeString.substring(0, languageIndex);
		}

		// Extract country
		int countryIndex = localeString.indexOf('_', languageIndex + 1);
		String country = null;
		if (countryIndex == -1) {
			// No further "_" so is "{language}_{country}"
			country = localeString.substring(languageIndex + 1);
			return new Locale(language, country);
		} else {
			// Assume all remaining is the variant so is
			// "{language}_{country}_{variant}"
			country = localeString.substring(languageIndex + 1, countryIndex);
			String variant = localeString.substring(countryIndex + 1);
			return new Locale(language, country, variant);
		}
	}

	public static void encryptCredentials(ObjectDelta delta, boolean encrypt, MidPointApplication app) {
		if (delta == null || delta.isEmpty()) {
			return;
		}

		PropertyDelta propertyDelta = delta.findPropertyDelta(
				new ItemPath(SchemaConstantsGenerated.C_CREDENTIALS, CredentialsType.F_PASSWORD, PasswordType.F_VALUE));
		if (propertyDelta == null) {
			return;
		}

		Collection<PrismPropertyValue<ProtectedStringType>> values = propertyDelta.getValues(ProtectedStringType.class);
		for (PrismPropertyValue<ProtectedStringType> value : values) {
			ProtectedStringType string = value.getValue();
			encryptProtectedString(string, encrypt, app);
		}
	}

	public static void encryptCredentials(PrismObject object, boolean encrypt, MidPointApplication app) {
		PrismContainer password = object
				.findContainer(new ItemPath(SchemaConstantsGenerated.C_CREDENTIALS, CredentialsType.F_PASSWORD));
		if (password == null) {
			return;
		}
		PrismProperty protectedStringProperty = password.findProperty(PasswordType.F_VALUE);
		if (protectedStringProperty == null
				|| protectedStringProperty.getRealValue(ProtectedStringType.class) == null) {
			return;
		}

		ProtectedStringType string = (ProtectedStringType) protectedStringProperty
				.getRealValue(ProtectedStringType.class);

		encryptProtectedString(string, encrypt, app);
	}

	public static void encryptProtectedString(ProtectedStringType string, boolean encrypt, MidPointApplication app) {
		if (string == null) {
			return;
		}
		Protector protector = app.getProtector();
		try {
			if (encrypt) {
				if (StringUtils.isEmpty(string.getClearValue())) {
					return;
				}
				protector.encrypt(string);
			} else {
				if (string.getEncryptedDataType() == null) {
					return;
				}
				protector.decrypt(string);
			}
		} catch (EncryptionException ex) {
			LoggingUtils.logException(LOGGER, "Couldn't encrypt protected string", ex);
		} catch (SchemaException e) {
			LoggingUtils.logException(LOGGER, "Couldn't encrypt/decrypt protected string", e);
		}
	}

	public static <T extends Selectable> List<T> getSelectedData(Table table) {
		DataTable dataTable = table.getDataTable();
		BaseSortableDataProvider<T> provider = (BaseSortableDataProvider<T>) dataTable.getDataProvider();

		List<T> selected = new ArrayList<T>();
		for (T bean : provider.getAvailableData()) {
			if (bean.isSelected()) {
				selected.add(bean);
			}
		}

		return selected;
	}

	public static Collection<ObjectDelta<? extends ObjectType>> createDeltaCollection(
			ObjectDelta<? extends ObjectType>... deltas) {
		Collection<ObjectDelta<? extends ObjectType>> collection = new ArrayList<ObjectDelta<? extends ObjectType>>();
		for (ObjectDelta delta : deltas) {
			collection.add(delta);
		}

		return collection;
	}

	public static boolean showResultInPage(OperationResult result) {
		if (result == null) {
			return false;
		}

		return !result.isSuccess() && !result.isHandledError() && !result.isInProgress();
	}

	public static String formatDate(XMLGregorianCalendar calendar) {
		if (calendar == null) {
			return null;
		}

		return formatDate(XmlTypeConverter.toDate(calendar));
	}

	public static String formatDate(Date date) {
		return formatDate(null, date);
	}

	public static String formatDate(String format, Date date) {
		if (date == null) {
			return null;
		}

		if (StringUtils.isEmpty(format)) {
			format = "EEEE, d. MMM yyyy HH:mm:ss";
		}
		Locale locale = Session.get().getLocale();
		if (locale == null) {
			locale = Locale.US;
		}
		SimpleDateFormat dateFormat = new SimpleDateFormat(format, locale);
		return dateFormat.format(date);
	}

    public static String getLocalizedDatePattern(String style){
        return DateTimeFormat.patternForStyle(style, getCurrentLocale());
    }

    public static Locale getCurrentLocale(){
        Locale locale = Session.get().getLocale();
        if (locale == null){
            locale = Locale.getDefault();
        }
        return locale;
    }

    public static boolean isActivationEnabled(PrismObject object) {
		Validate.notNull(object);

		PrismContainer activation = object.findContainer(UserType.F_ACTIVATION); // this
																					// is
																					// equal
																					// to
																					// account
																					// activation...
		if (activation == null) {
			return false;
		}

		ActivationStatusType status = (ActivationStatusType) activation
				.getPropertyRealValue(ActivationType.F_ADMINISTRATIVE_STATUS, ActivationStatusType.class);
		if (status == null) {
			return false;
		}

		// todo imrove with activation dates...
		return ActivationStatusType.ENABLED.equals(status);
	}

	public static boolean isSuccessOrHandledError(OperationResult result) {
		if (result == null) {
			return false;
		}

		return result.isSuccess() || result.isHandledError();
	}

	public static boolean isSuccessOrHandledErrorOrWarning(OperationResult result) {
		if (result == null) {
			return false;
		}

		return result.isSuccess() || result.isHandledError() || result.isWarning();
	}

	public static boolean isSuccessOrHandledErrorOrInProgress(OperationResult result) {
		if (result == null) {
			return false;
		}

		return result.isSuccess() || result.isHandledError() || result.isInProgress();
	}

	public static String createUserIcon(PrismObject<UserType> object) {
		UserType user = object.asObjectable();

		// if user has superuser role assigned, it's superuser
		for (AssignmentType assignment : user.getAssignment()) {
			ObjectReferenceType targetRef = assignment.getTargetRef();
			if (targetRef == null) {
				continue;
			}
			if (StringUtils.equals(targetRef.getOid(), SystemObjectsType.ROLE_SUPERUSER.value())) {
				return "fa fa-male text-danger";
			}
		}

		ActivationType activation = user.getActivation();
		if (activation != null && ActivationStatusType.DISABLED.equals(activation.getEffectiveStatus())) {
			return "fa fa-male text-muted";
		}

		return "fa fa-male";
	}
	
	public static String createRoleIcon(PrismObject<RoleType> object) {
		return "fa fa-street-view";
	}
	
	public static String createOrgIcon(PrismObject<OrgType> object) {
		return "fa fa-building";
	}
	
	public static String createResourceIcon(PrismObject<ResourceType> object) {
		return "fa fa-laptop";
	}
	
	public static String createShadowIcon(PrismObject<ShadowType> object) {
		ShadowType shadow = object.asObjectable();
		
		if (ShadowUtil.isProtected(object)){
			return "fa fa-shield";
		}
		
		switch (shadow.getKind()){
			case ACCOUNT: 
				return "fa fa-eye";
			case GENERIC:
				return "fa fa-institution";
			case ENTITLEMENT:
				return "fa fa-group";
					
		}
		return "fa fa-circle-o";
	}

	public static String createUserIconTitle(PrismObject<UserType> object) {
		UserType user = object.asObjectable();

		// if user has superuser role assigned, it's superuser
		for (AssignmentType assignment : user.getAssignment()) {
			ObjectReferenceType targetRef = assignment.getTargetRef();
			if (targetRef == null) {
				continue;
			}
			if (StringUtils.equals(targetRef.getOid(), SystemObjectsType.ROLE_SUPERUSER.value())) {
				return "User.superuser";
			}
		}

		ActivationType activation = user.getActivation();
		if (activation != null && ActivationStatusType.DISABLED.equals(activation.getEffectiveStatus())) {
			return "User.disabled";
		}

		return null;
	}

	public static double getSystemLoad() {
		com.sun.management.OperatingSystemMXBean operatingSystemMXBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory
				.getOperatingSystemMXBean();
		RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		int availableProcessors = operatingSystemMXBean.getAvailableProcessors();
		long prevUpTime = runtimeMXBean.getUptime();
		long prevProcessCpuTime = operatingSystemMXBean.getProcessCpuTime();

		try {
			Thread.sleep(150);
		} catch (Exception ignored) {
			// ignored
		}

		operatingSystemMXBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		long upTime = runtimeMXBean.getUptime();
		long processCpuTime = operatingSystemMXBean.getProcessCpuTime();
		long elapsedCpu = processCpuTime - prevProcessCpuTime;
		long elapsedTime = upTime - prevUpTime;

		double cpuUsage = Math.min(99F, elapsedCpu / (elapsedTime * 10000F * availableProcessors));

		return cpuUsage;
	}

	public static double getMaxRam() {
		int MB = 1024 * 1024;

		MemoryMXBean mBean = ManagementFactory.getMemoryMXBean();
		long maxHeap = mBean.getHeapMemoryUsage().getMax();
		long maxNonHeap = mBean.getNonHeapMemoryUsage().getMax();

		return (maxHeap + maxNonHeap) / MB;
	}

	public static double getRamUsage() {
		int MB = 1024 * 1024;

		MemoryMXBean mBean = ManagementFactory.getMemoryMXBean();
		long usedHead = mBean.getHeapMemoryUsage().getUsed();
		long usedNonHeap = mBean.getNonHeapMemoryUsage().getUsed();

		return (usedHead + usedNonHeap) / MB;
	}

	/**
	 * Checks table if has any selected rows ({@link Selectable} interface
	 * dtos), adds "single" parameter to selected items if it's not null. If
	 * table has no selected rows warn message is added to feedback panel, and
	 * feedback is refreshed through {@link AjaxRequestTarget}
	 *
	 * @param target
	 * @param single
	 *            this parameter is used for row actions when action must be
	 *            done only on chosen row.
	 * @param table
	 * @param page
	 * @param nothingWarnMessage
	 * @param <T>
	 * @return
	 */
	public static <T extends Selectable> List<T> isAnythingSelected(AjaxRequestTarget target, T single, Table table,
			PageBase page, String nothingWarnMessage) {
		List<T> selected;
		if (single != null) {
			selected = new ArrayList<T>();
			selected.add(single);
		} else {
			selected = WebComponentUtil.getSelectedData(table);
			if (selected.isEmpty()) {
				page.warn(page.getString(nothingWarnMessage));
				target.add(page.getFeedbackPanel());
			}
		}

		return selected;
	}

	public static void refreshFeedbacks(MarkupContainer component, final AjaxRequestTarget target) {
		component.visitChildren(IFeedback.class, new IVisitor<Component, Void>() {

			@Override
			public void component(final Component component, final IVisit<Void> visit) {
				target.add(component);
			}
		});
	}

	/*
	 * Methods used for providing prismContext into various objects.
	 */
	public static void revive(LoadableModel<?> loadableModel, PrismContext prismContext) throws SchemaException {
		if (loadableModel != null) {
			loadableModel.revive(prismContext);
		}
	}

	public static void revive(IModel<?> model, PrismContext prismContext) throws SchemaException {
		if (model != null && model.getObject() != null) {
			reviveObject(model.getObject(), prismContext);
		}
	}

	public static void reviveObject(Object object, PrismContext prismContext) throws SchemaException {
		if (object == null) {
			return;
		}
		if (object instanceof Collection) {
			for (Object item : (Collection) object) {
				reviveObject(item, prismContext);
			}
		} else if (object instanceof Revivable) {
			((Revivable) object).revive(prismContext);
		}
	}

	// useful for components other than those inheriting from PageBase
	public static PrismContext getPrismContext(Component component) {
		return ((MidPointApplication) component.getApplication()).getPrismContext();
	}

	public static void reviveIfNeeded(ObjectType objectType, Component component) {
		if (objectType != null && objectType.asPrismObject().getPrismContext() == null) {
			try {
				objectType.asPrismObject().revive(getPrismContext(component));
			} catch (SchemaException e) {
				throw new SystemException("Couldn't revive " + objectType + " because of schema exception", e);
			}
		}
	}

	public static List<String> getChannelList() {
		List<String> channels = new ArrayList<>();

		for (Channel channel : Channel.values()) {
			channels.add(channel.getChannel());
		}

		return channels;
	}

	public static List<QName> getMatchingRuleList() {
		List<QName> list = new ArrayList<>();

		String NS_MATCHING_RULE = "http://prism.evolveum.com/xml/ns/public/matching-rule-3";

		list.add(new QName(NS_MATCHING_RULE, "default", "mr"));
		list.add(StringIgnoreCaseMatchingRule.NAME);
		list.add(PolyStringStrictMatchingRule.NAME);
		list.add(PolyStringOrigMatchingRule.NAME);
		list.add(PolyStringNormMatchingRule.NAME);
		list.add(DistinguishedNameMatchingRule.NAME);

		return list;
	}

	public static boolean isObjectOrgManager(PrismObject<? extends ObjectType> object) {
		if (object == null || object.asObjectable() == null) {
			return false;
		}

		ObjectType objectType = object.asObjectable();
		List<ObjectReferenceType> parentOrgRefs = objectType.getParentOrgRef();

		for (ObjectReferenceType ref : parentOrgRefs) {
			if (ref.getRelation() != null && ref.getRelation().equals(SchemaConstants.ORG_MANAGER)) {
				return true;
			}
		}

		return false;
	}

	public static String createHumanReadableByteCount(long bytes) {
		int unit = 1024;
		if (bytes < unit)
			return bytes + "B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		char pre = "KMGTPE".charAt(exp - 1);
		return String.format("%.1f%sB", bytes / Math.pow(unit, exp), pre);
	}

	public static void setCurrentPage(Table table, ObjectPaging paging) {
		if (table == null) {
			return;
		}

		if (paging == null) {
			table.getDataTable().setCurrentPage(0);
			return;
		}

		long itemsPerPage = table.getDataTable().getItemsPerPage();
		long page = ((paging.getOffset() + itemsPerPage) / itemsPerPage) - 1;
		if (page < 0) {
			page = 0;
		}

		table.getDataTable().setCurrentPage(page);
	}

	public static PageBase getPageBase(Component component) {
		Page page = component.getPage();
		if (page instanceof PageBase) {
			return (PageBase) page;
		} else if (page instanceof PageDialog) {
			return ((PageDialog) page).getPageBase();
		} else {
			throw new IllegalStateException("Couldn't determine page base for " + page);
		}
	}

	public static <T extends Component> T theSameForPage(T object, PageReference containingPageReference) {
        Page containingPage = containingPageReference.getPage();
        if (containingPage == null) {
            return object;
        }
        String path = object.getPageRelativePath();
        T retval = (T) containingPage.get(path);
        if (retval == null) {
            return object;
//            throw new IllegalStateException("There is no component like " + object + " (path '" + path + "') on " + containingPage);
        }
        return retval;
    }
    
    public static String debugHandler(IRequestHandler handler) {
    	if (handler == null) {
    		return null;
    	}
    	if (handler instanceof RenderPageRequestHandler) {
    		return "RenderPageRequestHandler("+((RenderPageRequestHandler)handler).getPageClass().getName()+")";
    	} else {
    		return handler.toString();
    	}
    }
    
    public static ItemPath joinPath(ItemPath path, ItemPath deltaPath) {
		List<ItemPathSegment> newPath = new ArrayList<ItemPathSegment>();

		ItemPathSegment firstDeltaSegment = deltaPath != null ? deltaPath.first() : null;
		if (path != null) {
			for (ItemPathSegment seg : path.getSegments()) {
				if (seg.equivalent(firstDeltaSegment)) {
					break;
				}
				newPath.add(seg);
			}
		}
		if (deltaPath != null) {
			newPath.addAll(deltaPath.getSegments());
		}

		return new ItemPath(newPath);

	}

	public static <T extends ObjectType> T getObjectFromReference(ObjectReferenceType ref, Class<T> type) {
		if (ref == null || ref.asReferenceValue().getObject() == null) {
			return null;
		}
		Objectable object = ref.asReferenceValue().getObject().asObjectable();
		if (!type.isAssignableFrom(object.getClass())) {
			throw new IllegalStateException("Got " + object.getClass() + " when expected " + type + ": " + ObjectTypeUtil.toShortString(ref, true));
		}
		return (T) object;
	}

	public static void dispatchToObjectDetailsPage(ObjectReferenceType objectRef, PageBase page) {
		if (objectRef == null) {
			return;		// should not occur
		}
		QName type = objectRef.getType();
		PageParameters parameters = new PageParameters();
		parameters.add(OnePageParameterEncoder.PARAMETER, objectRef.getOid());
		if (RoleType.COMPLEX_TYPE.equals(type)) {
			page.setResponsePage(new PageRole(parameters, page));
		} else if (OrgType.COMPLEX_TYPE.equals(type)) {
			page.setResponsePage(new PageOrgUnit(parameters, page));
		} else if (UserType.COMPLEX_TYPE.equals(type)) {
			page.setResponsePage(new PageUser(parameters, page));
		} else if (ResourceType.COMPLEX_TYPE.equals(type)) {
			page.setResponsePage(new PageResource(parameters, page));
		} else if (TaskType.COMPLEX_TYPE.equals(type)) {
			page.setResponsePage(new PageTaskEdit(parameters));		// TODO: "back" page
		} else {
			// nothing to do
		}
	}

	public static boolean hasDetailsPage(PrismObject<?> object) {
		Class<?> clazz = object.getCompileTimeClass();
		if (clazz == null) {
			return false;
		}

		return AbstractRoleType.class.isAssignableFrom(clazz) ||
				UserType.class.isAssignableFrom(clazz) ||
				ResourceType.class.isAssignableFrom(clazz) ||
				TaskType.class.isAssignableFrom(clazz);
	}

	@NotNull
	public static TabbedPanel<ITab> createTabPanel(String id, final PageBase parentPage, final List<ITab> tabs) {
		TabbedPanel<ITab> tabPanel = new TabbedPanel<ITab>(id, tabs) {
			@Override
			protected WebMarkupContainer newLink(String linkId, final int index) {
				return new AjaxSubmitLink(linkId) {

					@Override
					protected void onError(AjaxRequestTarget target,
							org.apache.wicket.markup.html.form.Form<?> form) {
						super.onError(target, form);
						target.add(parentPage.getFeedbackPanel());
					}

					@Override
					protected void onSubmit(AjaxRequestTarget target,
							org.apache.wicket.markup.html.form.Form<?> form) {
						super.onSubmit(target, form);

						setSelectedTab(index);
						if (target != null) {
							target.add(findParent(TabbedPanel.class));
						}
					}

				};
			}
		};
		tabPanel.setOutputMarkupId(true);
		return tabPanel;
	}

}