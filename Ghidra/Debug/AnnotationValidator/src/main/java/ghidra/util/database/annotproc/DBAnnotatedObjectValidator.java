/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.util.database.annotproc;

import java.util.*;

import javax.lang.model.element.*;
import javax.tools.Diagnostic.Kind;

import ghidra.util.database.DBAnnotatedObject;
import ghidra.util.database.annot.*;

/**
 * Validate {@link DBAnnotatedObject}-related annotations on a given type element.
 * 
 * <p>
 * This class ensures that annotations such as {@link DBAnnotatedField}, {@link DBAnnotatedColumn},
 * and {@link DBAnnotatedObjectInfo} are applied correctly and consistently on the fields and
 * columns of a class.
 */
public class DBAnnotatedObjectValidator {
	private final ValidationContext ctx;
	private final TypeElement type;
	private final Map<String, DBAnnotatedFieldValidator> fieldsByName = new LinkedHashMap<>();
	private final Map<String, DBAnnotatedColumnValidator> columnsByName = new LinkedHashMap<>();

	/**
	 * Construct a new validator for the given type element within the specified validation context
	 * 
	 * @param ctx the validation context
	 * @param type the type element to be validated
	 */
	public DBAnnotatedObjectValidator(ValidationContext ctx, TypeElement type) {
		this.ctx = ctx;
		this.type = type;
	}

	/**
	 * Add a field annotated with {@link DBAnnotatedField} to be validator.
	 * 
	 * @param field the field element annotated with {@link DBAnnotatedField}
	 */
	public void addAnnotatedField(VariableElement field) {
		DBAnnotatedField annotation = field.getAnnotation(DBAnnotatedField.class);
		assert annotation != null;
		fieldsByName.put(annotation.column(), new DBAnnotatedFieldValidator(ctx, field));
	}

	/**
	 * Add a column annotated with {@link DBAnnotatedColumn} to the validator.
	 * 
	 * @param column the field element annotated with {@link DBAnnotatedColumn}
	 */
	public void addAnnotatedColumn(VariableElement column) {
		DBAnnotatedColumn annotation = column.getAnnotation(DBAnnotatedColumn.class);
		assert annotation != null;
		columnsByName.put(annotation.value(), new DBAnnotatedColumnValidator(ctx, column));
	}

	/**
	 * Validate the annotated fields, columns, and the type element itself.
	 * 
	 * <p>
	 * Checks for various annotation constraints and consistency rules.
	 */
	public void validate() {
		DBAnnotatedObjectInfo annotation = type.getAnnotation(DBAnnotatedObjectInfo.class);
		if (annotation != null && type.getKind() != ElementKind.CLASS) {
			ctx.messager.printMessage(Kind.ERROR,
				String.format("@%s cannot be applied to an interface",
					DBAnnotatedObjectInfo.class.getSimpleName()),
				type);
		}
		else if (annotation != null && type.getModifiers().contains(Modifier.ABSTRACT)) {
			ctx.messager.printMessage(Kind.ERROR,
				String.format("@%s cannot be applied to an abstract class",
					DBAnnotatedObjectInfo.class.getSimpleName()),
				type);
		}
		if (annotation != null && !ctx.isSubclass(type, ctx.DB_ANNOTATED_OBJECT_ELEM)) {
			ctx.messager.printMessage(Kind.ERROR,
				String.format("@%s can only be applied to subclasses of %s", "DBAnnotatedObject",
					DBAnnotatedObjectInfo.class.getSimpleName()));
		}
		if (annotation == null && !type.getModifiers().contains(Modifier.ABSTRACT)) {
			ctx.messager.printMessage(Kind.ERROR,
				String.format("Non-abstract subclasses of %s must have @%s annotation",
					"DBAnnotatedObject", DBAnnotatedObjectInfo.class.getSimpleName()),
				type);
		}
		if (annotation != null && annotation.version() < 0) {
			ctx.messager.printMessage(Kind.ERROR, String.format("@%s.version cannot be negative",
				DBAnnotatedObjectInfo.class.getSimpleName()), type);
		}

		validateFields();
		validateColumns();

		checkMissing();
	}

	/**
	 * Validate all fields annotated with {@link DBAnnotatedField}.
	 */
	protected void validateFields() {
		for (DBAnnotatedFieldValidator fv : fieldsByName.values()) {
			fv.validate();
		}
	}

	/**
	 * Validate all columns annotated with {@link DBAnnotatedColumn}.
	 */
	protected void validateColumns() {
		for (DBAnnotatedColumnValidator cv : columnsByName.values()) {
			cv.validate();
		}
	}

	/**
	 * Check for missing corresponding annotations between fields and columns.
	 */
	protected void checkMissing() {
		Set<String> names = new LinkedHashSet<>();
		names.addAll(fieldsByName.keySet());
		names.addAll(columnsByName.keySet());
		for (String n : names) {
			DBAnnotatedFieldValidator fv = fieldsByName.get(n);
			DBAnnotatedColumnValidator cv = columnsByName.get(n);
			if (fv == null && cv != null && !type.getModifiers().contains(Modifier.ABSTRACT)) {
				ctx.messager.printMessage(Kind.ERROR,
					String.format("@%s is missing corresponding @%s of the same column name: %s",
						DBAnnotatedColumn.class.getSimpleName(),
						DBAnnotatedField.class.getSimpleName(), n),
					cv.column);
			}
			if (fv != null && cv == null && !type.getModifiers().contains(Modifier.ABSTRACT)) {
				ctx.messager.printMessage(Kind.WARNING,
					String.format("@%s is missing corresponding @%s of the same column name: %s",
						DBAnnotatedField.class.getSimpleName(),
						DBAnnotatedColumn.class.getSimpleName(), n),
					fv.field);
			}
			if (fv != null && cv != null) {
				checkAccess(fv.field, cv.column, n);
			}
		}

	}

	/**
	 * Check that the access specifiers of the field and column are compatible.
	 * 
	 * @param field the field element
	 * @param column the column element
	 * @param name the name of the column
	 */
	protected void checkAccess(VariableElement field, VariableElement column, String name) {
		AccessSpec fieldSpec = AccessSpec.get(field.getModifiers());
		AccessSpec columnSpec = AccessSpec.get(column.getModifiers());
		if (!AccessSpec.isSameOrMorePermissive(fieldSpec, columnSpec)) {
			ctx.messager.printMessage(Kind.WARNING,
				String.format(
					"field with @%s should have same or greater access than field with" +
						" corresponding @%s for column name: %s",
					DBAnnotatedColumn.class.getSimpleName(), DBAnnotatedField.class.getSimpleName(),
					name),
				column);
		}
	}
}
