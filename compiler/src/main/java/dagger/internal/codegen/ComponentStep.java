package dagger.internal.codegen;

import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.squareup.javawriter.JavaWriter;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.internal.ScopedProvider;

public class ComponentStep implements ProcessingStep {
  private final Elements elements;
  private final Types types;
  private final Filer filer;
  private final BindingRepository bindingRepository;
  private final ProviderTyper providerTyper;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;

  ComponentStep(ProcessingEnvironment processingEnv, BindingRepository factoryRepository) {
    this.elements = processingEnv.getElementUtils();
    this.types = processingEnv.getTypeUtils();
    this.filer = processingEnv.getFiler();
    this.bindingRepository = factoryRepository;
    this.providerTyper = new ProviderTyper(types, elements);
    this.keyFactory = new KeyFactory(types, elements);
    this.bindingFactory = new BindingFactory(keyFactory);
  }

 static ImmutableMap<String, AnnotationValue> simplifyAnnotationValueMap(
     Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValueMap) {
   ImmutableMap.Builder<String, AnnotationValue> builder = ImmutableMap.builder();
   for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
       : annotationValueMap.entrySet()) {
     builder.put(entry.getKey().getSimpleName().toString(), entry.getValue());
   }
   return builder.build();
 }

 static Name getQualifiedName(DeclaredType type) {
   return type.asElement().accept(new SimpleElementVisitor6<Name, Void>() {
     @Override
     protected Name defaultAction(Element e, Void p) {
       throw new AssertionError("DeclaredTypes should be TypeElements");
     }

     @Override
     public Name visitType(TypeElement e, Void p) {
       return e.getQualifiedName();
     }
   }, null);
 }

 static Optional<AnnotationMirror> getAnnotationMirror(Element element,
     Class<? extends Annotation> annotationType) {
   String annotationName = annotationType.getName();
   for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
     if (getQualifiedName(annotationMirror.getAnnotationType()).contentEquals(annotationName)) {
       return Optional.of(annotationMirror);
     }
   }
   return Optional.absent();
 }

  ImmutableSet<TypeElement> asTypeSet(AnnotationValue value) {
    return value.accept(new SimpleAnnotationValueVisitor6<ImmutableSet<TypeElement>, Void>() {
      @Override public ImmutableSet<TypeElement> visitArray(List<? extends AnnotationValue> vals,
          Void p) {
        ImmutableSet.Builder<TypeElement> builder = new ImmutableSet.Builder<TypeElement>();
        for (AnnotationValue value : vals) {
          builder.add(value.accept(new SimpleAnnotationValueVisitor6<TypeElement, Void>() {
            @Override
            public TypeElement visitType(TypeMirror t, Void p) {
              return types.asElement(t).accept(new SimpleElementVisitor6<TypeElement, Void>() {
                @Override public TypeElement visitType(TypeElement e, Void p) {
                  return e;
                }
              }, null);
            }
          }, null));
        }
        return builder.build();
      }
    }, null);
  }

  private ImmutableSet<TypeElement> getAllModules(Iterable<TypeElement> seeds) {
    ImmutableSet.Builder<TypeElement> builder = ImmutableSet.<TypeElement>builder().addAll(seeds);
    for (TypeElement module : seeds) {
      Optional<AnnotationMirror> moduleMirror = getAnnotationMirror(module, Module.class);
      ImmutableMap<String, AnnotationValue> valueMap =
          simplifyAnnotationValueMap(elements.getElementValuesWithDefaults(moduleMirror.get()));
      ImmutableSet<TypeElement> includesTypes = asTypeSet(valueMap.get("includes"));
      builder.addAll(getAllModules(includesTypes));
    }
    return builder.build();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> components = roundEnv.getElementsAnnotatedWith(Component.class);
    for (Element component : components) {
      // first, make sure it's an interface
      component.accept(new ElementKindVisitor6<Void, Void>() {
        @Override
        public Void visitTypeAsInterface(TypeElement injectorInterface, Void p) {
          // look at the annotation and gather the dependencies (modules, injectors)
          Optional<AnnotationMirror> injectorMirror =
              getAnnotationMirror(injectorInterface, Component.class);
          ImmutableMap<String, AnnotationValue> valueMap = simplifyAnnotationValueMap(
              elements.getElementValuesWithDefaults(injectorMirror.get()));
          ImmutableSet<TypeElement> moduleTypes = getAllModules(asTypeSet(valueMap.get("modules")));


          // first pass: all of the bindings declared by the modules and injectors
          // this will leave us with a bunch of unresolved bindings that can _only_ be JIT bindings

          for (TypeElement module : moduleTypes) {
            // traverse the modules, collect the bindings
            List<ExecutableElement> moduleMethods =
                ElementFilter.methodsIn(elements.getAllMembers(module));
            for (ExecutableElement moduleMethod : moduleMethods) {
              if (moduleMethod.getAnnotation(Provides.class) != null) {
                Binding providerMethodBinding = bindingFactory.forProviderMethod(moduleMethod);
                bindingRepository.registerBinding(providerMethodBinding);
              }
            }
          }

          ImmutableSet<TypeElement> injectorTypes = asTypeSet(valueMap.get("dependencies"));
          for (TypeElement injector : injectorTypes) {
            List<ExecutableElement> injectorMethods =
                FluentIterable.from(ElementFilter.methodsIn(elements.getAllMembers(injector)))
                    .filter(new Predicate<ExecutableElement>() {
                      @Override public boolean apply(ExecutableElement e) {
                        return e.getModifiers().contains(ABSTRACT);
                      }
                    })
                    .toList();
            for (ExecutableElement injectorMethod : injectorMethods) {
              Binding injectorMethodBinding = bindingFactory.forInjectorMethod(injectorMethod);
              bindingRepository.registerBinding(injectorMethodBinding);
            }
          }

          // second pass: resolve all of the transitive dependencies

          Deque<Key> keysToResolve = Queues.newArrayDeque();

          // injector methods in _this_ injector
          List<ExecutableElement> injectorMethods = FluentIterable.from(
              ElementFilter.methodsIn(elements.getAllMembers(injectorInterface)))
                  .filter(new Predicate<ExecutableElement>() {
                    @Override public boolean apply(ExecutableElement e) {
                      return e.getModifiers().contains(ABSTRACT);
                    }
                  })
                  .toList();
          for (ExecutableElement injectorMethod : injectorMethods) {
            List<? extends VariableElement> parameters = injectorMethod.getParameters();
            Key injectorKey = keyFactory.forInjectorMethod(injectorMethod);
            if (parameters.isEmpty()) {
              // this is a "get" method
              keysToResolve.addLast(injectorKey);
            } else if (parameters.size() == 1) {
              // this is a member injection method
              // the return type must match the argument
            } else {
              // WTF is this?
            }
          }

          ImmutableSet.Builder<Binding> utilizedBindings = ImmutableSet.builder();

          Key keyToResolve;
          while ((keyToResolve = keysToResolve.pollLast()) != null) {
            Optional<Binding> binding = bindingRepository.getBindingForKey(keyToResolve);
            if (binding.isPresent()) {
              // good to go
              keysToResolve.addAll(binding.get().requiredKeys());
              utilizedBindings.add(binding.get());
            } else {
              // need to make a jit binding
              // TODO(gak)
            }
          }

          writeInjector(injectorInterface, moduleTypes, injectorTypes, injectorMethods,
              utilizedBindings.build());
          return null;
        }
      }, null);
    }
    return false;
  }

  private void writeInjector(TypeElement injectorInterface,
      ImmutableSet<TypeElement> moduleTypes,
      ImmutableSet<TypeElement> injectorTypes,
      List<ExecutableElement> injectorMethods,
      ImmutableSet<Binding> utilizedBindings) {
    ClassName injectorInterfaceName = ClassName.forTypeElement(injectorInterface);
    try {
      ClassName injectorName = injectorInterfaceName.peerNamed(
          "DaggerComponent_" + injectorInterfaceName.simpleName());
      JavaFileObject sourceFile = filer.createSourceFile(
          injectorName.fullyQualifiedName(), injectorInterface);

      final JavaWriter writer =
          new JavaWriter(new OutputStreamWriter(sourceFile.openOutputStream()));

      writer.emitPackage(injectorName.packageName());

      writer.emitImports(Provider.class, Generated.class, ScopedProvider.class, Inject.class);

      writer.emitAnnotation(Generated.class, JavaWriter.stringLiteral("dagger"));
      writer.beginType(injectorName.fullyQualifiedName(), "class",
          EnumSet.of(PUBLIC, FINAL), null,
          injectorInterface.getQualifiedName().toString());

      for (TypeElement module : moduleTypes) {
        ClassName moduleName = ClassName.forTypeElement(module);
        String moduleFieldName = moduleName.suggestedVariableName();
        writer.emitField(moduleName.fullyQualifiedName(),
            moduleFieldName,
            EnumSet.of(PRIVATE , FINAL),
            "new " + writer.compressType(moduleName.fullyQualifiedName()) + "()");
      }
      for (TypeElement injector : injectorTypes) {
        ClassName injectorDepName = ClassName.forTypeElement(injector);
        String injectorDepFieldName = injectorDepName.suggestedVariableName();
        writer.emitField(injectorDepName.fullyQualifiedName(),
            injectorDepFieldName,
            EnumSet.of(PRIVATE , FINAL));
      }

      for (final Binding binding : utilizedBindings) {
        String variableName = binding.providedKey().suggestedIdentifier();
        writer.emitField(Util.typeToString(providerTyper.scopedProviderType(binding)),
            variableName, EnumSet.of(PRIVATE, FINAL));
      }

      ImmutableList.Builder<String> constructorParameters = ImmutableList.builder();
      for (TypeElement injector : injectorTypes) {
        constructorParameters.add(injector.getQualifiedName().toString(),
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, injector.getSimpleName().toString()));
      }
      ImmutableList<String> manualConstructorParameters = constructorParameters.build();
      for (Binding binding : Lists.reverse(utilizedBindings.asList())) {
        constructorParameters
            .add(Util.typeToString(providerTyper.scopedProviderType(binding)))
            .add(binding.providedKey().suggestedIdentifier());
      }
      ImmutableList<String> injectConstructorParameters = constructorParameters.build();


      // This is the mechanism by which included modules ensure that they share the same providers
      // TODO(gak): make this work and reenable it
      // writer.emitAnnotation(Inject.class);
      if (!manualConstructorParameters.equals(injectConstructorParameters)) {
        writer.beginConstructor(EnumSet.of(PUBLIC), injectConstructorParameters,
            ImmutableList.<String>of());
        for (Binding binding : Lists.reverse(utilizedBindings.asList())) {
          String variableName = binding.providedKey().suggestedIdentifier();
          writer.emitStatement("this.%s = %s", variableName, variableName);
        }
        writer.endConstructor();
      }

      // TODO(gak): make this a factory method
      writer.beginConstructor(EnumSet.of(PUBLIC), manualConstructorParameters,
          ImmutableList.<String>of());
      for (TypeElement injector : injectorTypes) {
        writer.emitStatement("this.%s = %s",
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, injector.getSimpleName().toString()),
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, injector.getSimpleName().toString()));
      }
      for (Binding binding : Lists.reverse(utilizedBindings.asList())) {
        ImmutableList.Builder<String> factoryConstructorParameters = ImmutableList.builder();
        if (binding.bindingElement().getKind().equals(METHOD)) {
          String targetName = ClassName.forTypeElement(Elements2.asTypeElement(
              binding.bindingElement().getEnclosingElement())).suggestedVariableName();
          factoryConstructorParameters.add(targetName);
        }
        for (Key requiredKey : binding.requiredKeys()) {
          factoryConstructorParameters.add(requiredKey.suggestedIdentifier());
        }
        String variableName = binding.providedKey().suggestedIdentifier();
        Optional<AnnotationMirror> scopeAnnotation = binding.scopeAnnotation();
        writer.emitStatement(scopeAnnotation.isPresent()
                ? "this.%s = ScopedProvider.create(new %s(%s))"
                : "this.%s = new %s(%s)",
            variableName,
            writer.compressType(Factories.forBinding(binding).fullyQualifiedName()),
            Joiner.on(", ").join(factoryConstructorParameters.build()));
      }
      writer.endConstructor();

      for (ExecutableElement injectorMethod : injectorMethods) {
        writer.emitAnnotation(Override.class);
        writer.beginMethod(Util.typeToString(injectorMethod.getReturnType()),
            injectorMethod.getSimpleName().toString(), EnumSet.of(PUBLIC));
        Key key = keyFactory.forInjectorMethod(injectorMethod);
        writer.emitStatement("return %s.get()", key.suggestedIdentifier());
        writer.endMethod();
      }

      writer.endType();

      writer.close();
    } catch (IOException ex) {
    }
  }
}
