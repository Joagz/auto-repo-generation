package dev.joago.processors;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.google.auto.service.AutoService;

import dev.joago.annotations.AutoRepository;

@AutoService(Processor.class)
@SupportedAnnotationTypes("dev.joago.annotations.AutoRepository")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AutoRepositoryProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        annotations.forEach(annotation -> roundEnv.getElementsAnnotatedWith(AutoRepository.class).forEach(
                t -> {
                    try {
                        createAutoRepository(t);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }));
        return true;

    }

    public void createAutoRepository(Element element) throws IOException {

        final String classname = element.getSimpleName().toString();
        final String packagename = element.getEnclosingElement().toString();

        final String completeClassame = "%sAutoRepository".formatted(classname);
        final String completePackagename = "%s.%s".formatted(packagename, completeClassame);

        List<? extends Element> enclosedElements = element.getEnclosedElements();

        Set<Element> fields = new HashSet<>();

        for (Element e : enclosedElements) {
            if (e.getKind() == ElementKind.FIELD && e.getAnnotation(dev.joago.annotations.Field.class) != null) {
                fields.add(e);
            }
        }

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(completePackagename);

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println("package %s;".formatted(packagename));
            out.println("\nimport %s;".formatted(PagingAndSortingRepository.class.getName()));
            out.println("import %s;".formatted(Pageable.class.getName()));
            out.println("import %s;".formatted(Page.class.getName()));
            out.println("import %s;".formatted(Query.class.getName()));
            out.println("\npublic interface %s extends PagingAndSortingRepository<%s, Integer> {\n"
                    .formatted(completeClassame, classname));

            fields.forEach(f -> {
                out.println("""
                        \tpublic Page<%s> findBy%s(%s %s, Pageable pageable);
                            """.formatted(classname,
                        f.getSimpleName().toString().substring(0, 1).toUpperCase()
                                + f.getSimpleName().toString().substring(1),
                        "Object", f.getSimpleName()));
            });
            StringBuilder querySb = new StringBuilder();
            StringBuilder fieldSb = new StringBuilder();
            String dbName = element.getAnnotation(AutoRepository.class).databaseName();

            querySb.append("SELECT * FROM %s WHERE ".formatted(dbName));

            fields.forEach(f -> {
                fieldSb.append("Object %s,".formatted(f.getSimpleName()));
                querySb.append("\n\t\t(:%s IS NULL OR %s LIKE :%s) AND".formatted(f.getSimpleName(), f.getSimpleName(),
                        f.getSimpleName()));
            });
            fieldSb.deleteCharAt(fieldSb.length() - 1);
            querySb.delete(querySb.length() - 4, querySb.length());

            out.println("""
                        @Query(nativeQuery = true, value=\"""
                            %s
                            \""")
                        public Page<%s> findQuery(%s, Pageable pageable);
                    """.formatted(querySb.toString(), classname, fieldSb.toString()));

            out.println("\n}");

        } catch (Exception e) {

        }

    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        System.out.println("----------");

        System.out.println(processingEnv.getOptions());

    }
}
