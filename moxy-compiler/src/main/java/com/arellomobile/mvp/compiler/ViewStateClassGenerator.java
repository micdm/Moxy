package com.arellomobile.mvp.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.arellomobile.mvp.MvpProcessor;
import com.arellomobile.mvp.viewstate.strategy.AddToEndStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;


import static com.arellomobile.mvp.compiler.Util.fillGenerics;

/**
 * Date: 18.12.2015
 * Time: 13:24
 *
 * @author Yuri Shmakov
 */
final class ViewStateClassGenerator extends ClassGenerator<TypeElement>
{
	public static final String STATE_STRATEGY_TYPE_ANNOTATION = StateStrategyType.class.getName();
	public static final String DEFAULT_STATE_STRATEGY = AddToEndStrategy.class.getName() + ".class";

	private String mViewClassName;

	public boolean generate(TypeElement typeElement, List<ClassGeneratingParams> classGeneratingParamsList)
	{
		if (!typeElement.getTypeParameters().isEmpty())
		{
			throw new IllegalStateException("Code generation can't be applied to generic interface " + typeElement.getSimpleName());
		}

		String fullClassName = Util.getFullClassName(typeElement);

		ClassGeneratingParams classGeneratingParams = new ClassGeneratingParams();
		classGeneratingParams.setName(fullClassName + MvpProcessor.VIEW_STATE_SUFFIX);

		mViewClassName = getClassName(typeElement);

		String importSource = "package " + fullClassName.substring(0, fullClassName.lastIndexOf(".")) + ";\n" +
				"\n" +
				"import com.arellomobile.mvp.viewstate.MvpViewState;\n" +
				"import com.arellomobile.mvp.viewstate.ViewCommand;\n" +
				"import com.arellomobile.mvp.viewstate.ViewCommands;\n" +
				"import com.arellomobile.mvp.viewstate.strategy.AddToEndSingleStrategy;\n" +
				"import com.arellomobile.mvp.viewstate.strategy.AddToEndStrategy;\n" +
				"import com.arellomobile.mvp.viewstate.strategy.StateStrategy;\n";
		String classSource = "\npublic class " + fullClassName.substring(fullClassName.lastIndexOf(".") + 1) + "$$State extends MvpViewState<" + mViewClassName + "> implements " + mViewClassName + "\n" +
				"{\n" +
				"\tprivate ViewCommands<" + mViewClassName + "> mViewCommands = new ViewCommands<>();\n" +
				"\n" +
				"\t@Override\n" +
				"\tpublic void restoreState(" + mViewClassName + " view)\n" +
				"\t{\n" +
				"\t\tif (mViewCommands.isEmpty())\n" +
				"\t\t{\n" +
				"\t\t\treturn;\n" +
				"\t\t}\n" +
				"\n" +
				"\t\tmViewCommands.reapply(view);\n" +
				"\t}\n" +
				"\n";

		List<Method> methods = new ArrayList<>();

		String stateStrategyType = getStateStrategyType(typeElement);

		// Get methods for input class
		getMethods(Collections.<String, String>emptyMap(), typeElement, stateStrategyType, new ArrayList<Method>(), methods);

		// Add methods from super intefaces
		methods.addAll(iterateInterfaces(0, typeElement, stateStrategyType, Collections.<String, String>emptyMap(), methods, new ArrayList<Method>()));

		// Allow methods be with same names
		Map<String, Integer> methodsCounter = new HashMap<>();
		for (Method method : methods)
		{
			Integer counter = methodsCounter.get(method.name);

			if (counter == null || counter == 0)
			{
				counter = 0;
				method.uniqueName = method.name;
			}
			else
			{
				method.uniqueName = method.name + counter;
			}

			method.paramsClassName = method.uniqueName.substring(0, 1).toUpperCase() + method.uniqueName.substring(1) + "Params";

			counter++;
			methodsCounter.put(method.name, counter);
		}

		List<Method> observableReturningMethods = new ArrayList<>();
		for (Method method : methods)
		{
			String throwTypesString = join(", ", method.thrownTypes);
			if (throwTypesString.length() > 0)
			{
				throwTypesString = " throws " + throwTypesString;
			}

			String fieldName = "params";
			String argumentsString = "";
			int index = 0;
			for (Argument argument : method.arguments)
			{
				if (argument.name.equals(fieldName))
				{
					fieldName = "params" + index;
				}

				if (argumentsString.length() > 0)
				{
					argumentsString += ", ";
				}
				argumentsString += argument.name;
				index++;
			}

			String argumentClassName = "Void";
			String argumentsWrapperNewInstance = "null;\n";
			if (argumentsString.length() > 0)
			{
				argumentClassName = method.paramsClassName;
				argumentsWrapperNewInstance = "new " + method.paramsClassName + "(" + argumentsString + ");\n";
			}

			if (method.resultType.startsWith("rx.Observable")) {
				String subjectType = method.resultType.substring(method.resultType.indexOf("<") + 1, method.resultType.lastIndexOf(">"));
				String subjectName = method.name + "_Subject";
				classSource +=
						"\tprotected BehaviorSubject<" + subjectType + "> " + subjectName + " = BehaviorSubject.create();\n\n";
				classSource += "\t@Override\n" +
						"\tpublic " + method.genericType + method.resultType + " " + method.name + "(" + join(", ", method.arguments) + ")" + throwTypesString + "\n" +
						"\t{\n" +
						"\t\t" + argumentClassName + " " + fieldName + " = " + argumentsWrapperNewInstance +
						"\t\tmViewCommands.beforeApply(LocalViewCommand." + method.uniqueName + ", " + fieldName + ");\n" +
						"\n" +
						"\t\tif (mViews != null && !mViews.isEmpty())\n" +
						"\t\t{\n" +
						"\t\t\tmViewCommands.afterApply(LocalViewCommand." + method.uniqueName + ", " + fieldName + ");\n" +
						"\t\t}\n" +
						"\n" +
						"\t\treturn " + subjectName + ".asObservable();\n" +
						"\t}\n" +
						"\n";
				observableReturningMethods.add(method);
			} else {
				classSource += "\t@Override\n" +
						"\tpublic " + method.genericType + method.resultType + " " + method.name + "(" + join(", ", method.arguments) + ")" + throwTypesString + "\n" +
						"\t{\n" +
						"\t\t" + argumentClassName + " " + fieldName + " = " + argumentsWrapperNewInstance +
						"\t\tmViewCommands.beforeApply(LocalViewCommand." + method.uniqueName + ", " + fieldName + ");\n" +
						"\n" +
						"\t\tif (mViews == null || mViews.isEmpty())\n" +
						"\t\t{\n" +
						"\t\t\treturn;\n" +
						"\t\t}\n" +
						"\n" +
						"\t\tfor(" + mViewClassName + " view : mViews)\n" +
						"\t\t{\n" +
						"\t\t\tview." + method.name + "(" + argumentsString + ");\n" +
						"\t\t}\n" +
						"\n" +
						"\t\tmViewCommands.afterApply(LocalViewCommand." + method.uniqueName + ", " + fieldName + ");\n" +
						"\t}\n" +
						"\n";
			}
		}

		if (!observableReturningMethods.isEmpty()) {
			classSource +=
					"\tprotected Map<" + mViewClassName + ", Subscription> subscriptions = new HashMap<>();\n" +
					"\t\n" +
					"\t@Override\n" +
					"\tpublic void attachView(" + mViewClassName + " view) {\n" +
					"\t\tsuper.attachView(view);\n" +
					"\t\tif (subscriptions.containsKey(view)) {\n" +
					"\t\t\treturn;\n" +
					"\t\t}\n" +
					"\t\tCompositeSubscription subscription = new CompositeSubscription();\n";
			for (Method method: observableReturningMethods) {
				String observableType = method.resultType.substring(method.resultType.indexOf("<") + 1, method.resultType.lastIndexOf(">"));
				classSource +=
						"\t\tsubscription.add(view." + method.name + "().subscribe(new Action1<" + observableType + ">() {\n" +
						"\t\t\t@Override\n" +
						"\t\t\tpublic void call(" + observableType + " s) {\n" +
						"\t\t\t\t" + method.name + "_Subject.onNext(s);\n" +
						"\t\t\t}\n" +
						"\t\t}));\n";
			}
			classSource +=
					"\t\tsubscriptions.put(view, subscription);\n" +
					"\t}\n\n" +
					"\t@Override\n" +
					"\tpublic void detachView(" + mViewClassName + " view) {\n" +
					"\t\tif (subscriptions.containsKey(view)) {\n" +
					"\t\t\tsubscriptions.get(view).unsubscribe();\n" +
					"\t\t\tsubscriptions.remove(view);\n" +
					"\t\t}\n" +
					"\t\tsuper.detachView(view);\n" +
					"\t}\n\n";
		}

		if (!methods.isEmpty())
		{
			classSource = generateLocalViewCommand(mViewClassName, classSource, methods);
		}

		classSource += "}\n";

		if (!observableReturningMethods.isEmpty()) {
			importSource +=
					"import java.util.Map;\n" +
					"import java.util.HashMap;\n" +
					"import rx.Observable;\n" +
					"import rx.Subscription;\n" +
					"import rx.subjects.BehaviorSubject;\n" +
					"import rx.functions.Action1;\n" +
					"import rx.subscriptions.CompositeSubscription;\n";
		}

		classGeneratingParams.setBody(importSource + classSource);
		classGeneratingParamsList.add(classGeneratingParams);

		return true;
	}

	private List<Method> iterateInterfaces(int level, TypeElement parentElement, String parentDefaultStrategy, Map<String, String> parentTypes, List<Method> rootMethods, List<Method> superinterfacesMethods)
	{
		for (TypeMirror typeMirror : parentElement.getInterfaces())
		{
			final TypeElement anInterface = (TypeElement) ((DeclaredType) typeMirror).asElement();

			final List<? extends TypeMirror> typeArguments = ((DeclaredType) typeMirror).getTypeArguments();
			final List<? extends TypeParameterElement> typeParameters = anInterface.getTypeParameters();

			if (typeArguments.size() != typeParameters.size())
			{
				throw new IllegalArgumentException("Code generation for interface " + anInterface.getSimpleName() + " failed. Simplify your generics.");
			}

			Map<String, String> types = new HashMap<>();
			for (int i = 0; i < typeArguments.size(); i++)
			{
				types.put(typeParameters.get(i).toString(), typeArguments.get(i).toString());
			}

			Map<String, String> totalInterfaceTypes = new HashMap<>(typeParameters.size());
			for (int i = 0; i < typeArguments.size(); i++)
			{
				totalInterfaceTypes.put(typeParameters.get(i).toString(), fillGenerics(parentTypes, typeArguments.get(i)));

			}

			String defaultStrategy = parentDefaultStrategy != null ? parentDefaultStrategy : getStateStrategyType(anInterface);

			getMethods(totalInterfaceTypes, anInterface, defaultStrategy, rootMethods, superinterfacesMethods);

			iterateInterfaces(level + 1, anInterface, defaultStrategy, types, rootMethods, superinterfacesMethods);
		}

		return superinterfacesMethods;
	}

	private List<Method> getMethods(Map<String, String> types, TypeElement typeElement, String defaultStrategy, List<Method> rootMethods, List<Method> superinterfacesMethods)
	{
		for (Element element : typeElement.getEnclosedElements())
		{
			if (!(element instanceof ExecutableElement))
			{
				continue;
			}

			final ExecutableElement methodElement = (ExecutableElement) element;

			String strategyClass = defaultStrategy != null ? defaultStrategy : DEFAULT_STATE_STRATEGY;
			String methodTag = "\"" + methodElement.getSimpleName() + "\"";
			for (AnnotationMirror annotationMirror : methodElement.getAnnotationMirrors())
			{
				if (!annotationMirror.getAnnotationType().asElement().toString().equals(STATE_STRATEGY_TYPE_ANNOTATION))
				{
					continue;
				}

				final Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirror.getElementValues();
				final Set<? extends ExecutableElement> keySet = elementValues.keySet();

				for (ExecutableElement key : keySet)
				{
					if ("value()".equals(key.toString()))
					{
						strategyClass = elementValues.get(key).toString();
					}
					else if ("tag()".equals(key.toString()))
					{
						methodTag = elementValues.get(key).toString();
					}
				}
			}

			Map<String, String> methodTypes = new HashMap<>(types);

			final ExecutableType executableType = (ExecutableType) methodElement.asType();
			final List<? extends TypeVariable> typeVariables = executableType.getTypeVariables();
			if (!typeVariables.isEmpty())
			{
				for (TypeVariable typeVariable : typeVariables)
				{
					methodTypes.put(typeVariable.asElement().toString(), typeVariable.asElement().toString());
				}
			}

			String generics = "";
			int genericsCount = typeVariables.size();

			if (!typeVariables.isEmpty())
			{
				generics += "<";
				for (TypeVariable typeVariable : typeVariables)
				{
					if (generics.length() > 1)
					{
						generics += ", ";
					}

					final TypeMirror upperBound = typeVariable.getUpperBound();

					if (upperBound.toString().equals(Object.class.getCanonicalName()))
					{
						generics += typeVariable.asElement();
						continue;
					}

					final String filledGeneric = fillGenerics(methodTypes, upperBound);
					if (filledGeneric.startsWith("?"))
					{
						generics += filledGeneric.replaceFirst("\\?", typeVariable.asElement().toString());
					}
					else
					{
						generics += typeVariable.asElement() + " extends " + filledGeneric;
					}
				}
				generics += "> ";
			}

			final List<? extends VariableElement> parameters = methodElement.getParameters();

			List<Argument> arguments = new ArrayList<>();
			for (VariableElement parameter : parameters)
			{
				arguments.add(new Argument(fillGenerics(methodTypes, parameter.asType()), parameter.toString()));
			}

			List<String> throwTypes = new ArrayList<>();
			for (TypeMirror typeMirror : methodElement.getThrownTypes())
			{
				throwTypes.add(fillGenerics(methodTypes, typeMirror));
			}

			final Method method = new Method(genericsCount, generics, fillGenerics(methodTypes, methodElement.getReturnType()), methodElement.getSimpleName().toString(), arguments, throwTypes, strategyClass, methodTag, getClassName(typeElement));

			if (rootMethods.contains(method))
			{
				continue;
			}

			if (superinterfacesMethods.contains(method))
			{
				final Method existingMethod = superinterfacesMethods.get(superinterfacesMethods.indexOf(method));

				if (!existingMethod.stateStrategy.equals(method.stateStrategy))
				{
					throw new IllegalStateException("Both " + existingMethod.enclosedClass + " and " + method.enclosedClass + " has method " + method.name + "(" + method.arguments.toString().substring(1, method.arguments.toString().length() - 1) + ") with difference strategies. Override this method in " + mViewClassName + " or make strategies equals");
				}
				if (!existingMethod.tag.equals(method.tag))
				{
					throw new IllegalStateException("Both " + existingMethod.enclosedClass + " and " + method.enclosedClass + " has method " + method.name + "(" + method.arguments.toString().substring(1, method.arguments.toString().length() - 1) + ") with difference tags. Override this method in " + mViewClassName + " or make tags equals");
				}

				continue;
			}

			superinterfacesMethods.add(method);
		}

		return superinterfacesMethods;
	}

	private String getClassName(TypeElement typeElement)
	{
		String name = typeElement.getSimpleName().toString();

		Element enclosingElement = typeElement.getEnclosingElement();
		while (enclosingElement != null && enclosingElement.getKind() == ElementKind.CLASS)
		{
			name = enclosingElement.getSimpleName() + "." + name;
			enclosingElement = enclosingElement.getEnclosingElement();
		}

		return name;
	}

	private String generateLocalViewCommand(String viewClassName, String builder, List<Method> methods)
	{
		builder += "\tprivate enum LocalViewCommand implements ViewCommand<" + viewClassName + ">\n" +
				"\t{\n";

		boolean isFirstEnum = true;
		for (Method method : methods)
		{
			String argumentsString = "";
			for (Argument argument : method.arguments)
			{
				if (argumentsString.length() > 0)
				{
					argumentsString += ", ";
				}

				argumentsString += "params." + argument.name;
			}

			String generics = "";
			if (method.genericsCount > 0)
			{
				generics += '<';
				for (int i = 0; i < method.genericsCount; i++)
				{
					if (generics.length() > 1)
					{
						generics += ", ";
					}
					generics += '?';
				}
				generics += '>';
			}

			if (!isFirstEnum)
			{
				builder += ",\n";
			}
			isFirstEnum = false;

			builder += "\t\t" + method.uniqueName + "(" + method.stateStrategy + ", " + method.tag + ")\n" +
					"\t\t\t\t{\n" +
					"\t\t\t\t\t@Override\n" +
					"\t\t\t\t\tpublic void apply(" + viewClassName + " mvpView, Object paramsObject)\n" +
					"\t\t\t\t\t{\n" +
					(
							method.arguments.isEmpty() ?
									""
									:
									"\t\t\t\t\t\tfinal " + method.paramsClassName + generics + " params = (" + method.paramsClassName + ") paramsObject;\n"
					) +
					"\t\t\t\t\t\tmvpView." + method.name + "(" + argumentsString + ");\n" +
					"\t\t\t\t\t}\n" +
					"\t\t\t\t}";
		}

		builder += ";\n" +
				"\n" +
				"\t\tprivate Class<? extends StateStrategy> mStateStrategyType;\n" +
				"\t\tprivate String mTag;\n" +
				"\n" +
				"\t\tLocalViewCommand(Class<? extends StateStrategy> stateStrategyType, String tag)\n" +
				"\t\t{\n" +
				"\t\t\tmStateStrategyType = stateStrategyType;\n" +
				"\t\t\tmTag = tag;\n" +
				"\t\t}\n" +
				"\n" +
				"\t\t@Override\n" +
				"\t\tpublic Class<? extends StateStrategy> getStrategyType()\n" +
				"\t\t{\n" +
				"\t\t\treturn mStateStrategyType;\n" +
				"\t\t}\n" +
				"\n" +
				"\t\t@Override\n" +
				"\t\tpublic String getTag()\n" +
				"\t\t{\n" +
				"\t\t\treturn mTag;\n" +
				"\t\t}\n" +
				"\t}\n";

		for (Method method : methods)
		{
			if (method.arguments.isEmpty())
			{
				continue;
			}

			String argumentsInit = "";
			String argumentsBind = "";
			for (Argument argument : method.arguments)
			{
				argumentsInit += "\t\t" + argument.type + " " + argument.name + ";\n";
				argumentsBind += "\t\t\tthis." + argument.name + " = " + argument.name + ";\n";
			}

			builder += "\n\tprivate class " + method.paramsClassName + method.genericType + "\n" +
					"\t{\n" +
					argumentsInit +
					"\n" +
					"\t\t" + method.paramsClassName + "(" + join(", ", method.arguments) + ")\n" +
					"\t\t{\n" +
					argumentsBind +
					"\t\t}\n" +
					"\t}\n";
		}
		return builder;
	}

	public String getStateStrategyType(TypeElement typeElement)
	{
		for (AnnotationMirror annotationMirror : typeElement.getAnnotationMirrors())
		{
			if (!annotationMirror.getAnnotationType().asElement().toString().equals(STATE_STRATEGY_TYPE_ANNOTATION))
			{
				continue;
			}

			final Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirror.getElementValues();
			final Set<? extends ExecutableElement> keySet = elementValues.keySet();

			for (ExecutableElement key : keySet)
			{
				if ("value()".equals(key.toString()))
				{
					return elementValues.get(key).toString();
				}
			}
		}

		return null;
	}

	private static class Method
	{
		private int genericsCount; // add <?> to instance declaration
		String genericType;
		String resultType;
		String name;
		String uniqueName; // required for methods with same name but difference params
		String paramsClassName;
		List<Argument> arguments;
		List<String> thrownTypes;
		String stateStrategy;
		String tag;
		String enclosedClass;

		Method(int genericsCount, String genericType, String resultType, String name, List<Argument> arguments, List<String> thrownTypes, String stateStrategy, String methodTag, String enclosedClass)
		{
			this.genericsCount = genericsCount;
			this.genericType = genericType;
			this.resultType = resultType;
			this.name = name;
			this.arguments = arguments;
			this.thrownTypes = thrownTypes;
			this.stateStrategy = stateStrategy;
			this.tag = methodTag;
			this.enclosedClass = enclosedClass;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Method method = (Method) o;

			//noinspection SimplifiableIfStatement
			if (name != null ? !name.equals(method.name) : method.name != null) return false;
			return !(arguments != null ? !arguments.equals(method.arguments) : method.arguments != null);

		}

		@Override
		public int hashCode()
		{
			int result = name != null ? name.hashCode() : 0;
			result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
			return result;
		}

		@Override
		public String toString()
		{
			return "Method{ " + genericType + ' ' + resultType + ' ' + name + '(' + arguments + ") throws " + thrownTypes + '}';
		}
	}

	private static class Argument
	{
		String type;
		String name;

		public Argument(String type, String name)
		{
			this.type = type;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return type + " " + name;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Argument argument = (Argument) o;

			return !(type != null ? !type.equals(argument.type) : argument.type != null);
		}

		@Override
		public int hashCode()
		{
			return type != null ? type.hashCode() : 0;
		}
	}

	public static String join(CharSequence delimiter, Iterable tokens)
	{
		StringBuilder sb = new StringBuilder();
		boolean firstTime = true;
		for (Object token : tokens)
		{
			if (firstTime)
			{
				firstTime = false;
			}
			else
			{
				sb.append(delimiter);
			}
			sb.append(token);
		}
		return sb.toString();
	}
}
