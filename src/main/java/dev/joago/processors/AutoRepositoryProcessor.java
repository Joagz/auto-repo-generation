package dev.joago.processors;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.AnnotationTypeMismatchException;
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

import dev.joago.annotations.Field;
import dev.joago.enums.PrimaryKeyTypes;
import dev.joago.exceptions.IncompatibleAnnotationException;
import org.springframework.core.annotation.AnnotationConfigurationException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.google.auto.service.AutoService;

import dev.joago.annotations.AutoRepository;
import org.springframework.data.repository.query.Param;

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
                    } catch (IOException | IncompatibleAnnotationException e) {
                        e.printStackTrace();
                    }
                }));
        return true;

    }

    private String getElementType(PrimaryKeyTypes primaryKeyType){
        switch (primaryKeyType){
            case LONG: return "Long";
            case INTEGER: return "Integer";
            case STRING: return "String";
        }
        return "Integer";
    }

    public void createAutoRepository(Element element) throws IOException, IncompatibleAnnotationException {

        final String classname = element.getSimpleName().toString();
        final String packagename = element.getEnclosingElement().toString();
        final String primaryKeyType = getElementType(element.getAnnotation(AutoRepository.class).primaryKeyType());
        final String completeClassame = "%sAutoRepository".formatted(classname);
        final String completePackagename = "%s.%s".formatted(packagename, completeClassame);



        List<? extends Element> enclosedElements = element.getEnclosedElements();

        Set<Element> fields = new HashSet<>();

        for (Element e : enclosedElements) {
            if(e.getKind() == ElementKind.FIELD) {
                if ( e.getAnnotation(Id.class)  == null && e.getAnnotation(dev.joago.annotations.Field.class) != null){
                    fields.add(e);
                } else if(e.getAnnotation(Id.class)  != null) {
                    throw new IncompatibleAnnotationException(Id.class.getName(), dev.joago.annotations.Field.class.getName());
                }
            }
        }

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(completePackagename);

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println("package %s;".formatted(packagename));
            out.println("\nimport %s;".formatted(PagingAndSortingRepository.class.getName()));
            out.println("import %s;".formatted(JpaRepository.class.getName()));
            out.println("import %s;".formatted(Pageable.class.getName()));
            out.println("import %s;".formatted(Page.class.getName()));
            out.println("import %s;".formatted(Param.class.getName()));
            out.println("import %s;".formatted(Query.class.getName()));
            out.println("\npublic interface %s extends PagingAndSortingRepository<%s, %s>, JpaRepository<%s, %s> {\n"
                    .formatted(completeClassame, classname, primaryKeyType, classname, primaryKeyType));

            fields.forEach(f -> {
                out.println("""
                        \tpublic Page<%s> findBy%s(%s %s, Pageable pageable);
                            """.formatted(classname,
                        f.getSimpleName().toString().substring(0, 1).toUpperCase()
                                + f.getSimpleName().toString().substring(1),
                        "Object", toSnakeCase(f.getSimpleName().toString())));
            });
            StringBuilder querySb = new StringBuilder();
            String dbName = element.getAnnotation(AutoRepository.class).databaseName();

            querySb.append("SELECT * FROM %s WHERE ".formatted(dbName));

            fields.forEach(f -> {
                    querySb.append("\n\t\t(:#{#query.%s} IS NULL OR %s LIKE :#{#query.%s}) AND".formatted(toSnakeCase(f.getSimpleName().toString()), toSnakeCase(f.getSimpleName().toString()),
                            toSnakeCase(f.getSimpleName().toString())));
            });

            querySb.delete(querySb.length() - 4, querySb.length());

            out.println("""
                        @Query(nativeQuery = true, value=\"""
                            %s
                            \""")
                        public Page<%s> findQuery(@Param("query") Object %s, Pageable pageable);
                    """.formatted(querySb.toString(), classname, "queryDto"));

            out.println("\n}");

        } catch (Exception e) {

        }

    }

    private String toSnakeCase(String str) {
        String regex = "([a-z])([A-Z]+)";
        String replacement = "$1_$2";
        str = str
                .replaceAll(
                        regex, replacement)
                .toLowerCase();
        return str;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        System.out.println("----------");

        System.out.println(processingEnv.getOptions());

    }
}
