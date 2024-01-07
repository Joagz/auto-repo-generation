package dev.joago.processors;

import java.io.IOException;
import java.io.PrintWriter;
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

import dev.joago.enums.PrimaryKeyTypes;
import dev.joago.exceptions.IncompatibleAnnotationException;
import com.google.auto.service.AutoService;

import dev.joago.annotations.AutoRepository;

@AutoService(Processor.class)
@SupportedAnnotationTypes("dev.joago.annotations.AutoRepository")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AutoRepositoryProcessor extends AbstractProcessor {

    private String classname;
    private String packagename;
    private String primaryKeyType;
    private String completeClassname;
    private String completePackagename;
    private Set<Element> fields = new HashSet<>();

    String PagingAndSortingRepositoryImport = "org.springframework.data.repository.PagingAndSortingRepository";
    String JpaRepositoryImport = "org.springframework.data.jpa.repository.JpaRepository";
    String PageableImport = "org.springframework.data.domain.Pageable";
    String PageImport = "org.springframework.data.domain.Page";
    String ParamImport = "org.springframework.data.repository.query.Param";
    String QueryImport = "org.springframework.data.mongodb.repository.Query";
    String MongoRepositoryImport = "org.springframework.data.mongodb.repository.MongoRepository";

    public boolean checkDependencies() {
        try {
            Class.forName(PageableImport);
            Class.forName(PageImport);
            Class.forName(ParamImport);
            Class.forName(QueryImport);
        } catch (ClassNotFoundException e) {
            System.err.println("Your project requires jakarta.persistence dependency to work with AutoRepository");
            return false;
        }
        try {
            Class.forName(MongoRepositoryImport);
        } catch (ClassNotFoundException e) {
            try {
                Class.forName(PagingAndSortingRepositoryImport);
                Class.forName(JpaRepositoryImport);
            } catch (ClassNotFoundException ex) {
                System.err.println("Your project requires either Spring Data MongoDB or Spring Data JPA to work with AutoRepository");
                return false;
            }
        }
        return true;
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (checkDependencies()) {
            annotations.forEach(annotation -> roundEnv.getElementsAnnotatedWith(AutoRepository.class).forEach(
                    t -> {
                        try {
                            determineRepositoryType(t);
                        } catch (IOException | IncompatibleAnnotationException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }));
            return true;
        } else {
            return false;
        }

    }

    private String getElementType(PrimaryKeyTypes primaryKeyType) {
        switch (primaryKeyType) {
            case LONG:
                return "Long";
            case INTEGER:
                return "Integer";
            case STRING:
                return "String";
        }
        return "Integer";
    }

    private void determineRepositoryType(Element element) throws IOException, IncompatibleAnnotationException, ClassNotFoundException {

        Class Entity = Class.forName("jakarta.persistence.Entity");
        Class Document = Class.forName("org.springframework.data.mongodb.core.mapping.Document");

        if (element.getAnnotation(Entity) != null)
            createJpaRepository(element);
        else if (element.getAnnotation(Document) != null)
            createMongoRepository(element);
        else createJpaRepository(element);

    }

    public void cleanBuilderProperties(){
        classname = null;
        packagename = null;
        primaryKeyType = null;
        completeClassname = null;
        completePackagename = null;
        fields = new HashSet<>();
    }

    public void createBuilder(Element element) {
        classname = element.getSimpleName().toString();
        packagename = element.getEnclosingElement().toString();
        primaryKeyType = getElementType(element.getAnnotation(AutoRepository.class).primaryKeyType());
        completeClassname = "%sAutoRepository".formatted(classname);
        completePackagename = "%s.%s".formatted(packagename, completeClassname);

        List<? extends Element> enclosedElements = element.getEnclosedElements();
        try {
            Class Id = Class.forName("jakarta.persistence.Id");

            for (Element e : enclosedElements) {

                if (e.getAnnotation(Id) != null) {
                    continue;
                } else if (e.getKind() == ElementKind.FIELD) {
                    if (e.getAnnotation(dev.joago.annotations.Field.class) != null) {
                        fields.add(e);
                    }
                }
            }
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            return;
        }
    }

    public void createJpaRepository(Element element) throws IOException {
        createBuilder(element);
        if (fields.isEmpty()) {
            return;
        }
        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(completePackagename);

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println("package %s;".formatted(packagename));
            out.println("\nimport %s;".formatted(PagingAndSortingRepositoryImport));
            out.println("import %s;".formatted(JpaRepositoryImport));
            out.println("import %s;".formatted(PageableImport));
            out.println("import %s;".formatted(PageImport));
            out.println("import %s;".formatted(ParamImport));
            out.println("import %s;".formatted(QueryImport));
            out.println("\npublic interface %s extends PagingAndSortingRepository<%s, %s>, JpaRepository<%s, %s> {\n"
                    .formatted(completeClassname, classname, primaryKeyType, classname, primaryKeyType));

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
                        public %s findQuery(@Param("query") Object %s);
                    """.formatted(querySb.toString(), classname, "queryDto"));

            out.println("\n}");

        } catch (Exception e) {

        }
        cleanBuilderProperties();

    }

    public void createMongoRepository(Element element) throws IOException {

        createBuilder(element);

        if (fields.isEmpty()) {
            return;
        }

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(completePackagename);

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.println("package %s;".formatted(packagename));
            out.println("\nimport %s;".formatted(MongoRepositoryImport));
            out.println("import %s;".formatted(PageableImport));
            out.println("import %s;".formatted(PageImport));
            out.println("import %s;".formatted(ParamImport));
            out.println("import %s;".formatted(QueryImport));
            out.println("\npublic interface %s extends MongoRepository<%s, %s> {\n"
                    .formatted(completeClassname, classname, primaryKeyType));

            fields.forEach(f -> {
                out.println("""
                        \tpublic Page<%s> findBy%s(%s %s, Pageable pageable);
                            """.formatted(classname,
                        f.getSimpleName().toString().substring(0, 1).toUpperCase()
                                + f.getSimpleName().toString().substring(1),
                        "Object", toSnakeCase(f.getSimpleName().toString())));
            });
            StringBuilder querySb = new StringBuilder();

            querySb.append("{");

            fields.forEach(f -> {
                querySb.append("\n\t\t%s: {$regex : :#{#query.%s}},".formatted(toSnakeCase(f.getSimpleName().toString()),
                        toSnakeCase(f.getSimpleName().toString())));
            });

            querySb.deleteCharAt(querySb.length() - 1);
            querySb.append("}");

            out.println("""
                        @Query(value=\"""
                            %s
                            \""")
                        public %s findQuery(@Param("query") Object %s);
                    """.formatted(querySb.toString(), classname, "queryDto"));

            out.println("\n}");

        } catch (Exception e) {

        }
        cleanBuilderProperties();
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
