# AUTOREPOSITORY

## Brief Description
Spring Data JPA provides various repository implementations such as JpaRepository, PagingAndSortingRepository, ... and such. This annotation is meant for creating the repository implementation automatically, in order to avoid boilerplate code.

```java

@AutoRepository(databaseName = "customers") // <- Main annotation (databaseName must be provided)
@Entity(table="customers")
public class Customer {

    @Id
    @Field
    Integer id;

    @Field // <- Define readable fields (to query on)
    String email;

    @Field
    String name;

  /* ... Getters, Setters and Constructor. */
}
```

This is all you require to create the repository. The default implementation is PagingAndSortingRepository, once compiled will result in ```CustomerAutoRepository.java``` file:

```java
public interface CustomerAutoRepository extends PagingAndSortingRepository<Customer, Integer> {

	public Page<Customer> findById(Object id, Pageable pageable);

	public Page<Customer> findByEmail(Object email, Pageable pageable);

	public Page<Customer> findByName(Object name, Pageable pageable);

    @Query(nativeQuery = true, value="""
        SELECT * FROM customers WHERE 
	(:id IS NULL OR id LIKE :id) AND
	(:email IS NULL OR email LIKE :email) AND
	(:name IS NULL OR name LIKE :name)
        """)
    public Page<Customer> findQuery(Object id,Object email,Object name, Pageable pageable);
}
```

The annotation processor made the following tasks:
* Created the repository defaults
* Used ```@Field``` to define a method to query by those fields
* Created a ```findQuery``` method to query by every parameter, ignoring null.

### Final Comments
This project was created to solve a common problem I had when querying models with lots of parameters, further implementations and functionalities will come in future realeases.

Made by Joaquín Gómez : joagomez.dev@gmail.com
