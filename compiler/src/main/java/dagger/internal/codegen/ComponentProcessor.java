package dagger.internal.codegen;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import dagger.Component;
import dagger.Module;

public final class ComponentProcessor extends AbstractProcessor {
  private ImmutableList<ProcessingStep> steps;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    BindingRepository factoryRepository = new BindingRepository();
    this.steps = ImmutableList.of(
        new InjectStep(processingEnv, factoryRepository),
        new ModuleStep(processingEnv, factoryRepository),
        new ComponentStep(processingEnv, factoryRepository));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (ProcessingStep step : steps) {
      step.process(annotations, roundEnv);
    }
    return false;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        Module.class.getName(), Component.class.getName(), Inject.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_6;
  }
}
