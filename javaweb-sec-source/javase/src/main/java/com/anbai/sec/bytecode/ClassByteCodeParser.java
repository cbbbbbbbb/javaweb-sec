package com.anbai.sec.bytecode;

import com.alibaba.fastjson.JSON;
import org.javaweb.utils.FileUtils;

import java.io.*;
import java.util.*;

import static com.anbai.sec.bytecode.ClassByteCodeParser.Constant.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ClassByteCodeParser {

	/**
	 * 转换为数据输入流
	 */
	private DataInputStream dis;

	/**
	 * Class文件魔数
	 */
	private int magic;

	/**
	 * Class小版本号
	 */
	private int minor;

	/**
	 * Class大版本号
	 */
	private int major;

	/**
	 * 常量池中的对象数量
	 */
	private int poolCount;

	/**
	 * 创建常量池Map
	 */
	private final Map<Integer, Map<String, Object>> constantPoolMap = new LinkedHashMap<>();

	/**
	 * 类访问修饰符
	 */
	private int accessFlags;

	/**
	 * thisClass
	 */
	private int thisClass;

	/**
	 * superClass
	 */
	private int superClass;

	/**
	 * 接口数
	 */
	private int interfacesCount;

	/**
	 * 接口Index数组
	 */
	private String[] interfaces;

	/**
	 * 成员变量数量
	 */
	private int fieldsCount;

	/**
	 * 成员变量数组
	 */
	private final Set<Map<String, Object>> fieldList = new HashSet<>();

	/**
	 * 方法数
	 */
	private int methodsCount;

	/**
	 * 属性数
	 */
	private int attributesCount;

	/**
	 * 枚举常量池类型，兼容到JDK15
	 */
	public enum Constant {
		CONSTANT_UTF8(1, "A UTF-8 encoded Unicode string"),
		CONSTANT_INTEGER(3, "An int literal value"),
		CONSTANT_FLOAT(4, "A float literal value"),
		CONSTANT_LONG(5, "A long literal value"),
		CONSTANT_DOUBLE(6, "A double literal value"),
		CONSTANT_CLASS(7, "A symbolic reference to a class or interface"),
		CONSTANT_STRING(8, "A String literal value"),
		CONSTANT_FIELD_REF(9, "A symbolic reference to a field"),
		CONSTANT_METHOD_REF(10, "A symbolic reference to a method declared in a class"),
		CONSTANT_INTERFACE_METHOD_REF(11, "A symbolic reference to a method declared in an interface"),
		CONSTANT_NAME_AND_TYPE(12, "Part of a symbolic reference to a field or method"),
		CONSTANT_METHOD_HANDLE(15, "represent a method handle"),
		CONSTANT_METHOD_TYPE(16, "represent a method type"),
		CONSTANT_DYNAMIC(17, "represent entities directly,"),
		CONSTANT_INVOKE_DYNAMIC(18, "represent entities directly,"),
		CONSTANT_MODULE(19, "represent a module"),
		CONSTANT_PACKAGE(20, "represent a package exported or opened by a module");

		private final int flag;

		private final String desc;

		Constant(int flag, String desc) {
			this.flag = flag;
			this.desc = desc;
		}

		public int getFlag() {
			return flag;
		}

		public String getDesc() {
			return desc;
		}
	}

	/**
	 * 解析Class字节码
	 *
	 * @param in 类字节码输入流
	 * @throws IOException 解析IO异常
	 */
	private void parseByteCode(InputStream in) throws IOException {
		this.dis = new DataInputStream(in);

		// u4 magic;
		int magic = dis.readInt();

		// 校验文件魔数
		if (0xCAFEBABE == magic) {
			this.magic = magic;

			// u2 minor_version
			this.minor = dis.readUnsignedShort();

			// u2 major_version;
			this.major = dis.readUnsignedShort();

			// 解析常量池数据
			parseConstantPool();

			// u2 access_flags;
			this.accessFlags = dis.readUnsignedShort();

			// u2 this_class;
			this.thisClass = dis.readUnsignedShort();

			// u2 super_class;
			this.superClass = dis.readUnsignedShort();

			// u2 interfaces_count;
			this.interfacesCount = dis.readUnsignedShort();

			// 创建接口Index数组
			this.interfaces = new String[interfacesCount];

			// u2 interfaces[interfaces_count];
			for (int i = 0; i < interfacesCount; i++) {
				int index = dis.readUnsignedShort();

				// 设置接口名称
				this.interfaces[i] = getConstantPoolValue(index, "nameValue");
			}

			// u2 fields_count;
			this.fieldsCount = dis.readUnsignedShort();

			// field_info fields[fields_count];
			for (int i = 0; i < this.fieldsCount; i++) {
//				field_info {
//					u2 access_flags;
//					u2 name_index;
//					u2 descriptor_index;
//					u2 attributes_count;
//					attribute_info attributes[attributes_count];
//				}

				Map<String, Object> fieldMap = new LinkedHashMap<>();

				// u2 access_flags;
				int fieldAccessFlags = dis.readUnsignedShort();
				fieldMap.put("access", fieldAccessFlags);

				// u2 name_index;
				int fieldNameIndex = dis.readUnsignedShort();
				fieldMap.put("name", getConstantPoolValue(fieldNameIndex, "value"));

				// u2 descriptor_index;
				int fieldDescriptorIndex = dis.readUnsignedShort();
				fieldMap.put("desc", getConstantPoolValue(fieldDescriptorIndex, "value"));

				// u2 attributes_count;
				int fieldAttributesCount = dis.readUnsignedShort();
				fieldMap.put("attributesCount", fieldAttributesCount);

				// 读取成员变量属性信息
				readAttributes(fieldAttributesCount);

				this.fieldList.add(fieldMap);
			}

			// u2 methods_count;
			this.methodsCount = dis.readUnsignedShort();

			// method_info methods[methods_count];
			for (int i = 0; i < this.methodsCount; i++) {
//				method_info {
//					u2 access_flags;
//					u2 name_index;
//					u2 descriptor_index;
//					u2 attributes_count;
//					attribute_info attributes[attributes_count];
//				}

				Map<String, Object> methodMap = new LinkedHashMap<>();

				// u2 access_flags;
				int methodAccessFlags = dis.readUnsignedShort();
				methodMap.put("access", methodAccessFlags);

				// u2 name_index;
				int methodNameIndex = dis.readUnsignedShort();
				methodMap.put("name", getConstantPoolValue(methodNameIndex, "value"));

				// u2 descriptor_index;
				int methodDescriptorIndex = dis.readUnsignedShort();
				methodMap.put("desc", getConstantPoolValue(methodDescriptorIndex, "value"));

				// u2 attributes_count;
				int methodAttributesCount = dis.readUnsignedShort();
				methodMap.put("attributesCount", methodAttributesCount);

				// attribute_info attributes[attributes_count];
				readAttributes(methodAttributesCount);
			}

			// u2 attributes_count;
			this.attributesCount = dis.readUnsignedShort();

			// attribute_info attributes[attributes_count];
			readAttributes(attributesCount);
		} else {
			throw new RuntimeException("Class文件格式错误!");
		}
	}

	/**
	 * 通过常量池中的索引ID和名称获取常量池中的值
	 *
	 * @param index     索引ID
	 * @param nameValue 键名
	 * @param <T>       返回的数据类型
	 * @return 常量池中的值
	 */
	private <T> T getConstantPoolValue(int index, String nameValue) {
		return (T) constantPoolMap.get(index).get(nameValue);
	}

	/**
	 * 解析Attributes
	 *
	 * @param attrCount Attributes数量
	 * @throws IOException 读取数据IO异常
	 */
	private void readAttributes(int attrCount) throws IOException {
		// attribute_info attributes[attributes_count];
		for (int j = 0; j < attrCount; j++) {
//			attribute_info {
//				u2 attribute_name_index;
//				u4 attribute_length;
//				u1 info[attribute_length];
//			}

			// u2 attribute_name_index;
			int attributeNameIndex = dis.readUnsignedShort();

			// 属性名称
			String attributeName = getConstantPoolValue(attributeNameIndex, "value");

			// u4 attribute_length;
			int attributeLength = dis.readInt();

			// u1 info[attribute_length];
			if ("ConstantValue".equals(attributeName)) {
//				ConstantValue_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 constantvalue_index;
//				}

				int index = dis.readUnsignedShort();
			} else if ("Code".equals(attributeName)) {
//				Code_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 max_stack;
//					u2 max_locals;
//					u4 code_length;
//					u1 code[code_length];
//					u2 exception_table_length;
//					{ u2 start_pc;
//						u2 end_pc;
//						u2 handler_pc;
//						u2 catch_type;
//					} exception_table[exception_table_length];
//					u2 attributes_count;
//					attribute_info attributes[attributes_count];
//				}

				int    maxStack   = dis.readUnsignedShort();
				int    maxLocals  = dis.readUnsignedShort();
				int    codeLength = dis.readInt();
				byte[] bytes      = new byte[codeLength];

				dis.read(bytes);

				int exceptionTableLength = dis.readUnsignedShort();

				for (int i = 0; i < exceptionTableLength; i++) {
					int startPc   = dis.readUnsignedShort();
					int endPc     = dis.readUnsignedShort();
					int handlerPc = dis.readUnsignedShort();
					int catchType = dis.readUnsignedShort();
				}

				int attributesCount = dis.readShort();

				readAttributes(attributesCount);
			} else if ("StackMapTable".equals(attributeName)) {
//				StackMapTable_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 number_of_entries;
//					stack_map_frame entries[number_of_entries];
//				}

				int numberOfEntries = dis.readUnsignedShort();

				for (int i = 0; i < numberOfEntries; i++) {
					int frameType = dis.readUnsignedByte();

//					union stack_map_frame {
//						same_frame;
//						same_locals_1_stack_item_frame;
//						same_locals_1_stack_item_frame_extended;
//						chop_frame;
//						same_frame_extended;
//						append_frame;
//						full_frame;
//					}

					if (frameType >= 0 && frameType <= 63) {
						// same_frame 0-63

//						same_frame {
//							u1 frame_type = SAME; /* 0-63 */
//						}
					} else if (frameType >= 64 && frameType <= 127) {
						// same_locals_1_stack_item_frame 64-127

//						same_locals_1_stack_item_frame {
//							u1 frame_type = SAME_LOCALS_1_STACK_ITEM; /* 64-127 */
//							verification_type_info stack[1];
//						}

						processAttrTag();
					} else if (frameType == 247) {
						// same_locals_1_stack_item_frame_extended 247

//						same_locals_1_stack_item_frame_extended {
//							u1 frame_type = SAME_LOCALS_1_STACK_ITEM_EXTENDED; /* 247 */
//							u2 offset_delta;
//							verification_type_info stack[1];
//						}

						int offsetDelta = dis.readUnsignedShort();

						processAttrTag();
					} else if (frameType >= 248 && frameType <= 250) {
						//  chop_frame 248-250

//						chop_frame {
//							u1 frame_type = CHOP; /* 248-250 */
//							u2 offset_delta;
//						}

						int offsetDelta = dis.readUnsignedShort();
					} else if (frameType == 251) {
						// same_frame_extended 251

//						same_frame_extended {
//							u1 frame_type = SAME_FRAME_EXTENDED; /* 251 */
//							u2 offset_delta;
//						}

						int offsetDelta = dis.readUnsignedShort();
					} else if (frameType >= 252 && frameType <= 254) {
						// append_frame 252-254

//						append_frame {
//							u1 frame_type = APPEND; /* 252-254 */
//							u2 offset_delta;
//							verification_type_info locals[frame_type - 251];
//						}

						int offsetDelta = dis.readUnsignedShort();

						for (int k = 0; k < frameType - 251; k++) {
							processAttrTag();
						}
					} else {
						// full_frame 255

//						full_frame {
//							u1 frame_type = FULL_FRAME; /* 255 */
//							u2 offset_delta;
//							u2 number_of_locals;
//							verification_type_info locals[number_of_locals];
//							u2 number_of_stack_items;
//							verification_type_info stack[number_of_stack_items];
//						}

						// offset_delta;
						int offsetDelta = dis.readUnsignedShort();

						// u2 number_of_locals;
						int numberOfLocals = dis.readUnsignedShort();

						// verification_type_info locals[number_of_locals];
						for (int k = 0; k < numberOfLocals; k++) {
							processAttrTag();
						}

						// u2 number_of_stack_items;
						int numberOfStackItems = dis.readUnsignedShort();

						// verification_type_info stack[number_of_stack_items];
						for (int k = 0; k < numberOfStackItems; k++) {
							processAttrTag();
						}
					}
				}
			} else if ("Exceptions".equals(attributeName)) {
//				Exceptions_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 number_of_exceptions;
//					u2 exception_index_table[number_of_exceptions];
//				}

				int numberOfExceptions = dis.readUnsignedShort();

				for (int i = 0; i < numberOfExceptions; i++) {
					int index = dis.readUnsignedShort();
				}
			} else if ("InnerClasses".equals(attributeName)) {
//				InnerClasses_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 number_of_classes;
//					{ u2 inner_class_info_index;
//						u2 outer_class_info_index;
//						u2 inner_name_index;
//						u2 inner_class_access_flags;
//					} classes[number_of_classes];
//				}

				int numberOfClasses = dis.readUnsignedShort();

				for (int i = 0; i < numberOfClasses; i++) {
					// u2 inner_class_info_index;
					int innerClassInfoIndex = dis.readUnsignedShort();

					// u2 outer_class_info_index;
					int outerClassInfoIndex = dis.readUnsignedShort();

					// u2 inner_name_index;
					int innerNameIndex = dis.readUnsignedShort();

					// u2 inner_class_access_flags;
					int innerClassAccessFlags = dis.readUnsignedShort();
				}
			} else if ("EnclosingMethod".equals(attributeName)) {
//				EnclosingMethod_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 class_index;
//					u2 method_index;
//				}

				int classIndex  = dis.readUnsignedShort();
				int methodIndex = dis.readUnsignedShort();
			} else if ("Synthetic".equals(attributeName)) {
//				Synthetic_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//				}
			} else if ("Signature".equals(attributeName)) {
//				Signature_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 signature_index;
//				}

				int signatureIndex = dis.readUnsignedShort();
			} else if ("SourceFile".equals(attributeName)) {
//				SourceFile_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 sourcefile_index;
//				}

				int sourceFileIndex = dis.readUnsignedShort();
			} else if ("SourceDebugExtension".equals(attributeName)) {
//				SourceDebugExtension_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u1 debug_extension[attribute_length];
//				}

				byte[] bytes = new byte[attributeLength];

				dis.read(bytes);
			} else if ("LineNumberTable".equals(attributeName)) {
//				LineNumberTable_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 line_number_table_length;
//					{ u2 start_pc;
//						u2 line_number;
//					} line_number_table[line_number_table_length];
//				}

				int lineNumberTableLength = dis.readUnsignedShort();

				for (int i = 0; i < lineNumberTableLength; i++) {
					int startPc    = dis.readUnsignedShort();
					int lineNumber = dis.readUnsignedShort();
				}
			} else if ("LocalVariableTable".equals(attributeName)) {
//				LocalVariableTable_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 local_variable_table_length;
//					{ u2 start_pc;
//						u2 length;
//						u2 name_index;
//						u2 descriptor_index;
//						u2 index;
//					} local_variable_table[local_variable_table_length];
//				}

				int localVariableTableLength = dis.readUnsignedShort();

				for (int i = 0; i < localVariableTableLength; i++) {
					// u2 start_pc;
					int startPc = dis.readUnsignedShort();

					// u2 length;
					int length = dis.readUnsignedShort();

					// u2 name_index; 参数名称
					String name = getConstantPoolValue(dis.readUnsignedShort(), "value");

					// u2 descriptor_index; 参数描述符
					String desc = getConstantPoolValue(dis.readUnsignedShort(), "value");

					// u2 index;
					int index = dis.readUnsignedShort();
				}
			} else if ("LocalVariableTypeTable".equals(attributeName)) {
//				LocalVariableTypeTable_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 local_variable_type_table_length;
//					{ u2 start_pc;
//						u2 length;
//						u2 name_index;
//						u2 signature_index;
//						u2 index;
//					} local_variable_type_table[local_variable_type_table_length];
//				}

				int localVariableTypeTableLength = dis.readUnsignedShort();

				for (int i = 0; i < localVariableTypeTableLength; i++) {
					// u2 start_pc;
					int startPc = dis.readUnsignedShort();

					// u2 length;
					int length = dis.readUnsignedShort();

					// u2 name_index;
					int nameIndex = dis.readUnsignedShort();

					// u2 signature_index;
					int signatureIndex = dis.readUnsignedShort();

					// u2 index;
					int index = dis.readUnsignedShort();
				}
			} else if ("Deprecated".equals(attributeName)) {
//				Deprecated_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//				}
			} else if ("RuntimeVisibleAnnotations".equals(attributeName)) {
//				RuntimeVisibleAnnotations_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 num_annotations;
//					annotation annotations[num_annotations];
//				}

				int numAnnotations = dis.readUnsignedShort();

				for (int i = 0; i < numAnnotations; i++) {
					readAnnotation();
				}
			} else if ("RuntimeInvisibleAnnotations".equals(attributeName)) {
//				RuntimeInvisibleAnnotations_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 num_annotations;
//					annotation annotations[num_annotations];
//				}

				int numAnnotations = dis.readUnsignedShort();

				for (int i = 0; i < numAnnotations; i++) {
					readAnnotation();
				}
			} else if ("RuntimeVisibleParameterAnnotations".equals(attributeName)) {
//				RuntimeVisibleParameterAnnotations_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u1 num_parameters;
//					{ u2 num_annotations;
//						annotation annotations[num_annotations];
//					} parameter_annotations[num_parameters];
//				}

				int numParameters = dis.readUnsignedByte();

				for (int i = 0; i < numParameters; i++) {
					int numAnnotations = dis.readUnsignedShort();

					for (int k = 0; k < numAnnotations; k++) {
						readAnnotation();
					}
				}
			} else if ("RuntimeInvisibleParameterAnnotations".equals(attributeName)) {
//				RuntimeInvisibleParameterAnnotations_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u1 num_parameters;
//					{ u2 num_annotations;
//						annotation annotations[num_annotations];
//					} parameter_annotations[num_parameters];
//				}

				int numParameters = dis.readUnsignedByte();

				for (int i = 0; i < numParameters; i++) {
					int numAnnotations = dis.readUnsignedShort();

					for (int k = 0; k < numAnnotations; k++) {
						readAnnotation();
					}
				}
			} else if ("RuntimeVisibleTypeAnnotations".equals(attributeName)) {
//				RuntimeVisibleTypeAnnotations_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 num_annotations;
//					type_annotation annotations[num_annotations];
//				}

				int numAnnotations = dis.readUnsignedShort();

				for (int i = 0; i < numAnnotations; i++) {
					readTypeAnnotation();
				}
			} else if ("RuntimeInvisibleTypeAnnotations".equals(attributeName)) {
//				RuntimeInvisibleTypeAnnotations_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 num_annotations;
//					type_annotation annotations[num_annotations];
//				}

				int numAnnotations = dis.readUnsignedShort();

				for (int i = 0; i < numAnnotations; i++) {
					readTypeAnnotation();
				}
			} else if ("AnnotationDefault".equals(attributeName)) {
//				AnnotationDefault_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					element_value default_value;
//				}

				readElementType();
			} else if ("BootstrapMethods".equals(attributeName)) {
//				BootstrapMethods_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 num_bootstrap_methods;
//					{ u2 bootstrap_method_ref;
//						u2 num_bootstrap_arguments;
//						u2 bootstrap_arguments[num_bootstrap_arguments];
//					} bootstrap_methods[num_bootstrap_methods];
//				}

				int numBootstrapMethods = dis.readUnsignedShort();

				for (int i = 0; i < numBootstrapMethods; i++) {
					// u2 bootstrap_method_ref;
					int bootstrapMethodRef = dis.readUnsignedShort();

					// u2 num_bootstrap_arguments;
					int numBootstrapArguments = dis.readUnsignedShort();

					// u2 bootstrap_arguments[num_bootstrap_arguments];
					int bootstrapArguments = dis.readUnsignedShort();

					for (int k = 0; k < bootstrapArguments; k++) {
						int     bootstrapMethodRef2    = dis.readUnsignedShort();
						int     numBootstrapArguments2 = dis.readUnsignedShort();
						short[] bootstrapArguments2    = new short[numBootstrapArguments2];

						for (int l = 0; l < numBootstrapArguments2; l++) {
							bootstrapArguments2[l] = dis.readShort();
						}
					}
				}
			} else if ("MethodParameters".equals(attributeName)) {
//				MethodParameters_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u1 parameters_count;
//					{ u2 name_index;
//						u2 access_flags;
//					} parameters[parameters_count];
//				}

				int parametersCount = dis.readUnsignedByte();

				for (int i = 0; i < parametersCount; i++) {
					// u2 name_index;
					int nameIndex = dis.readUnsignedShort();

					// u2 access_flags;
					int accessFlags = dis.readUnsignedShort();
				}
			} else if ("Module".equals(attributeName)) {
//				Module_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 module_name_index;
//					u2 module_flags;
//					u2 module_version_index;
//					u2 requires_count;
//					{ u2 requires_index;
//						u2 requires_flags;
//						u2 requires_version_index;
//					} requires[requires_count];
//					u2 exports_count;
//					{ u2 exports_index;
//						u2 exports_flags;
//						u2 exports_to_count;
//						u2 exports_to_index[exports_to_count];
//					} exports[exports_count];
//					u2 opens_count;
//					{ u2 opens_index;
//						u2 opens_flags;
//						u2 opens_to_count;
//						u2 opens_to_index[opens_to_count];
//					} opens[opens_count];
//					u2 uses_count;
//					u2 uses_index[uses_count];
//					u2 provides_count;
//					{ u2 provides_index;
//						u2 provides_with_count;
//						u2 provides_with_index[provides_with_count];
//					} provides[provides_count];
//				}

				// u2 module_name_index;
				int moduleNameIndex = dis.readUnsignedShort();

				// u2 module_flags;
				int moduleFlags = dis.readUnsignedShort();

				// u2 module_version_index;
				int moduleVersionIndex = dis.readUnsignedShort();

				// u2 requires_count;
				int requiresCount = dis.readUnsignedShort();

				for (int i = 0; i < requiresCount; i++) {
					// u2 requires_index;
					int requiresIndex = dis.readUnsignedShort();

					// u2 requires_flags;
					int requiresFlags = dis.readUnsignedShort();

					// u2 requires_version_index;
					int requiresVersionIndex = dis.readUnsignedShort();
				}

				// u2 exports_count;
				int exportsCount = dis.readUnsignedShort();

				// u2 exports_index;
				int exportsIndex = dis.readUnsignedShort();

				// u2 exports_flags;
				int exportsFlags = dis.readUnsignedShort();

				// u2 exports_to_count;
				int exportsToCount = dis.readUnsignedShort();

				// u2 exports_to_index[exports_to_count];
				int exportsToIndex = dis.readUnsignedShort();

				// u2 opens_count;
				int opensCount = dis.readUnsignedShort();

				for (int i = 0; i < opensCount; i++) {
					// u2 opens_index;
					int opensIndex = dis.readUnsignedShort();

					// u2 opens_flags;
					int opensFlags = dis.readUnsignedShort();

					// u2 opens_to_count;
					int opensToCount = dis.readUnsignedShort();

					// u2 opens_to_index[opens_to_count];
					for (int k = 0; k < opensToCount; k++) {

					}
				}

				// u2 uses_count;
				int usesCount = dis.readUnsignedShort();

				// u2 uses_index[uses_count];
				for (int i = 0; i < usesCount; i++) {

				}

				// u2 provides_count;
				int providesCount = dis.readUnsignedShort();

				for (int i = 0; i < providesCount; i++) {
					// u2 provides_index;
					int providesIndex = dis.readUnsignedShort();

					// u2 provides_with_count;
					int providesWithCount = dis.readUnsignedShort();

					// u2 provides_with_index[provides_with_count];
					for (int k = 0; k < providesWithCount; k++) {

					}
				}
			} else if ("ModulePackages".equals(attributeName)) {
//				ModulePackages_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 package_count;
//					u2 package_index[package_count];
//				}

				int packageCount = dis.readUnsignedShort();

				for (int i = 0; i < packageCount; i++) {

				}
			} else if ("ModuleMainClass".equals(attributeName)) {
//				ModuleMainClass_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 main_class_index;
//				}

				int mainClassIndex = dis.readUnsignedShort();
			} else if ("NestHost".equals(attributeName)) {
//				NestHost_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 host_class_index;
//				}

				int hostClassIndex = dis.readUnsignedShort();
			} else if ("NestMembers".equals(attributeName)) {
//				NestMembers_attribute {
//					u2 attribute_name_index;
//					u4 attribute_length;
//					u2 number_of_classes;
//					u2 classes[number_of_classes];
//				}

				int numberOfClasses = dis.readUnsignedShort();

				for (int i = 0; i < numberOfClasses; i++) {

				}
			}
		}
	}

	private void readAnnotation() throws IOException {
//		annotation {
//			u2 type_index;
//			u2 num_element_value_pairs;
//			{ u2 element_name_index;
//				element_value value;
//			} element_value_pairs[num_element_value_pairs];
//		}

		// u2 type_index;
		int typeIndex = dis.readUnsignedShort();

		// u2 num_element_value_pairs;
		int numElementValuePairs = dis.readUnsignedShort();

		for (int i = 0; i < numElementValuePairs; i++) {
			// u2 element_name_index;
			int elementNameIndex = dis.readUnsignedShort();

			// element_value value;
			readElementType();
		}
	}

	private void readTypeAnnotation() {

	}

	private void readElementType() throws IOException {
//			element_value {
//				u1 tag;
//				union {
//					u2 const_value_index;
//					{ u2 type_name_index;
//						u2 const_name_index;
//					} enum_const_value;
//					u2 class_info_index;
//					annotation annotation_value;
//					{ u2 num_values;
//						element_value values[num_values];
//					} array_value;
//				} value;
//			}
		char tag = (char) dis.readUnsignedByte();

		// tag Item Type                value Item           Constant Type
		// B        byte                const_value_index    CONSTANT_Integer
		// C        char                const_value_index    CONSTANT_Integer
		// D        double              const_value_index    CONSTANT_Double
		// F        float               const_value_index    CONSTANT_Float
		// I        int                 const_value_index    CONSTANT_Integer
		// J        long                const_value_index    CONSTANT_Long
		// S        short               const_value_index    CONSTANT_Integer
		// Z        boolean             const_value_index    CONSTANT_Integer
		// s        String              const_value_index    CONSTANT_Utf8
		// e        Enum type           enum_const_value     Not applicable
		// c        Class               class_info_index     Not applicable
		// @        Annotation type     annotation_value     Not applicable
		// [        Array type          array_value          Not applicable
		if (tag == 'B' || tag == 'C' || tag == 'D' || tag == 'F' || tag == 'I' ||
				tag == 'J' || tag == 'S' || tag == 'Z' || tag == 's') {

			int constValueIndex = dis.readUnsignedShort();
		} else if (tag == 'e') {
			int typeNameIndex  = dis.readUnsignedShort();
			int constNameIndex = dis.readUnsignedShort();
		} else if (tag == 'c') {
			int classInfoIndex = dis.readUnsignedShort();
		} else if (tag == '@') {
			int typeIndex            = dis.readUnsignedShort();
			int numElementValuePairs = dis.readUnsignedShort();

			for (int i = 0; i < numElementValuePairs; i++) {
				int elementNameIndex = dis.readUnsignedShort();

				readElementType();
			}
		} else if (tag == '[') {
			int numValues = dis.readUnsignedShort();

			for (int j = 0; j < numValues; j++) {
				readElementType();
			}
		}
	}

	private void processAttrTag() throws IOException {
		int tag = dis.readUnsignedByte();

//		union verification_type_info {
//			Top_variable_info;
//			Integer_variable_info;
//			Float_variable_info;
//			Long_variable_info;
//			Double_variable_info;
//			Null_variable_info;
//			UninitializedThis_variable_info;
//			Object_variable_info;
//			Uninitialized_variable_info;
//		}

		if (tag == 0) {
			// Top_variable_info
		} else if (tag == 1) {
			// Integer_variable_info
		} else if (tag == 2) {
			// Float_variable_info
		} else if (tag == 3) {
			// Double_variable_info
		} else if (tag == 4) {
			// Long_variable_info
		} else if (tag == 5) {
			// Null_variable_info
		} else if (tag == 6) {
			// UninitializedThis_variable_info
		} else if (tag == 7) {
			// Object_variable_info
			int poolIndex = dis.readUnsignedShort();
		} else if (tag == 8) {
			// Uninitialized_variable_info
			int offset = dis.readUnsignedShort();
		}
	}

	/**
	 * 链接常量池中的引用
	 */
	private void linkConstantPool() {
		for (Integer id : constantPoolMap.keySet()) {
			Map<String, Object> valueMap = constantPoolMap.get(id);

			if (!valueMap.containsKey("value")) {
				Map<String, Object> newMap = new LinkedHashMap<>();

				for (String key : valueMap.keySet()) {
					if (key.endsWith("Index")) {
						Object value = recursionValue((Integer) valueMap.get(key));

						if (value != null) {
							String newKey = key.substring(0, key.indexOf("Index"));

							newMap.put(newKey + "Value", value);
						}
					}
				}

				valueMap.putAll(newMap);
			}
		}
	}

	/**
	 * 递归查找ID对应的常量池中的值
	 *
	 * @param id 常量池ID
	 * @return 常量池中存储的值
	 */
	private Object recursionValue(Integer id) {
		Map<String, Object> map = constantPoolMap.get(id);

		if (map.containsKey("value")) {
			return map.get("value");
		}

		for (String key : map.keySet()) {
			if (key.endsWith("Index")) {
				Integer value = (Integer) map.get(key);

				return recursionValue(value);
			}
		}

		return null;
	}

	/**
	 * 解析常量池数据
	 *
	 * @throws IOException 数据解析异常
	 */
	private void parseConstantPool() throws IOException {
		// u2 constant_pool_count;
		this.poolCount = dis.readUnsignedShort();

		// cp_info constant_pool[constant_pool_count-1];
		for (int i = 1; i <= poolCount - 1; i++) {
			int                 tag = dis.readUnsignedByte();
			Map<String, Object> map = new LinkedHashMap<>();

			if (tag == CONSTANT_UTF8.flag) {
//				CONSTANT_Utf8_info {
//					u1 tag;
//					u2 length;
//					u1 bytes[length];
//				}

				int    length = dis.readUnsignedShort();
				byte[] bytes  = new byte[length];
				dis.read(bytes);

				map.put("tag", CONSTANT_UTF8);
				map.put("value", new String(bytes, UTF_8));
			} else if (tag == CONSTANT_INTEGER.flag) {
//				CONSTANT_Integer_info {
//					u1 tag;
//					u4 bytes;
//				}

				map.put("tag", CONSTANT_INTEGER);
				map.put("value", dis.readInt());
			} else if (tag == CONSTANT_FLOAT.flag) {
//				CONSTANT_Float_info {
//					u1 tag;
//					u4 bytes;
//				}

				map.put("tag", CONSTANT_FLOAT);
				map.put("value", dis.readFloat());
			} else if (tag == CONSTANT_LONG.flag) {
//				CONSTANT_Long_info {
//					u1 tag;
//					u4 high_bytes;
//					u4 low_bytes;
//				}

				map.put("tag", CONSTANT_LONG);
				map.put("value", dis.readLong());
			} else if (tag == CONSTANT_DOUBLE.flag) {
//				CONSTANT_Double_info {
//					u1 tag;
//					u4 high_bytes;
//					u4 low_bytes;
//				}

				map.put("tag", CONSTANT_DOUBLE);
				map.put("value", dis.readDouble());
			} else if (tag == CONSTANT_CLASS.flag) {
//				CONSTANT_Class_info {
//					u1 tag;
//					u2 name_index;
//				}

				int nameIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_CLASS);
				map.put("nameIndex", nameIndex);
			} else if (tag == CONSTANT_STRING.flag) {
//				CONSTANT_String_info {
//					u1 tag;
//					u2 string_index;
//				}

				int stringIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_STRING);
				map.put("stringIndex", stringIndex);
			} else if (tag == CONSTANT_FIELD_REF.flag) {
//				CONSTANT_Fieldref_info {
//					u1 tag;
//					u2 class_index;
//					u2 name_and_type_index;
//				}

				int classIndex       = dis.readUnsignedShort();
				int nameAndTypeIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_FIELD_REF);
				map.put("classIndex", classIndex);
				map.put("nameAndTypeIndex", nameAndTypeIndex);
			} else if (tag == CONSTANT_METHOD_REF.flag) {
//				CONSTANT_Methodref_info {
//					u1 tag;
//					u2 class_index;
//					u2 name_and_type_index;
//				}

				int classIndex       = dis.readUnsignedShort();
				int nameAndTypeIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_METHOD_REF);
				map.put("classIndex", classIndex);
				map.put("nameAndTypeIndex", nameAndTypeIndex);
			} else if (tag == CONSTANT_INTERFACE_METHOD_REF.flag) {
//				CONSTANT_InterfaceMethodref_info {
//					u1 tag;
//					u2 class_index;
//					u2 name_and_type_index;
//				}

				int classIndex       = dis.readUnsignedShort();
				int nameAndTypeIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_INTERFACE_METHOD_REF);
				map.put("classIndex", classIndex);
				map.put("nameAndTypeIndex", nameAndTypeIndex);
			} else if (tag == CONSTANT_NAME_AND_TYPE.flag) {
//				CONSTANT_NameAndType_info {
//					u1 tag;
//					u2 name_index;
//					u2 descriptor_index;
//				}

				int nameIndex       = dis.readUnsignedShort();
				int descriptorIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_NAME_AND_TYPE);
				map.put("nameIndex", nameIndex);
				map.put("descriptorIndex", descriptorIndex);
			} else if (tag == CONSTANT_METHOD_HANDLE.flag) {
//				CONSTANT_MethodHandle_info {
//					u1 tag;
//					u1 reference_kind;
//					u2 reference_index;
//				}

				int referenceKind  = dis.readUnsignedByte();
				int referenceIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_METHOD_HANDLE);
				map.put("referenceKind", referenceKind);
				map.put("referenceIndex", referenceIndex);
			} else if (tag == CONSTANT_METHOD_TYPE.flag) {
//				CONSTANT_MethodType_info {
//					u1 tag;
//					u2 descriptor_index;
//				}

				int descriptorIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_METHOD_TYPE);
				map.put("descriptorIndex", descriptorIndex);
			} else if (tag == CONSTANT_DYNAMIC.flag) {
//				CONSTANT_Dynamic_info {
//					u1 tag;
//					u2 bootstrap_method_attr_index;
//					u2 name_and_type_index;
//				}

				int bootstrapMethodAttrIndex = dis.readUnsignedShort();
				int nameAndTypeIndex         = dis.readUnsignedShort();

				map.put("tag", CONSTANT_DYNAMIC);
				map.put("bootstrapMethodAttrIndex", bootstrapMethodAttrIndex);
				map.put("nameAndTypeIndex", nameAndTypeIndex);
			} else if (tag == CONSTANT_INVOKE_DYNAMIC.flag) {
//				CONSTANT_InvokeDynamic_info {
//					u1 tag;
//					u2 bootstrap_method_attr_index;
//					u2 name_and_type_index;
//				}

				int bootstrapMethodAttrIndex = dis.readUnsignedShort();
				int nameAndTypeIndex         = dis.readUnsignedShort();

				map.put("tag", CONSTANT_INVOKE_DYNAMIC);
				map.put("bootstrapMethodAttrIndex", bootstrapMethodAttrIndex);
				map.put("nameAndTypeIndex", nameAndTypeIndex);
			} else if (tag == CONSTANT_MODULE.flag) {
//				CONSTANT_Module_info {
//					u1 tag;
//					u2 name_index;
//				}

				int nameIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_MODULE);
				map.put("nameIndex", nameIndex);
			} else if (tag == CONSTANT_PACKAGE.flag) {
//				CONSTANT_Package_info {
//					u1 tag;
//					u2 name_index;
//				}

				int nameIndex = dis.readUnsignedShort();

				map.put("tag", CONSTANT_PACKAGE);
				map.put("nameIndex", nameIndex);
			}

			// Long和Double是宽类型，占两位
			if (tag == CONSTANT_LONG.flag || tag == CONSTANT_DOUBLE.flag) {
				i++;
			}

			constantPoolMap.put(i, map);
		}

		// 链接常量池中的引用
		linkConstantPool();
	}

	public DataInputStream getDis() {
		return dis;
	}

	public int getMagic() {
		return magic;
	}

	public int getMinor() {
		return minor;
	}

	public int getMajor() {
		return major;
	}

	public int getPoolCount() {
		return poolCount;
	}

	public Map<Integer, Map<String, Object>> getConstantPoolMap() {
		return constantPoolMap;
	}

	public int getAccessFlags() {
		return accessFlags;
	}

	public int getThisClass() {
		return thisClass;
	}

	public int getSuperClass() {
		return superClass;
	}

	public int getInterfacesCount() {
		return interfacesCount;
	}

	public String[] getInterfaces() {
		return interfaces;
	}

	public int getFieldsCount() {
		return fieldsCount;
	}

	public Set<Map<String, Object>> getFieldList() {
		return fieldList;
	}

	public int getMethodsCount() {
		return methodsCount;
	}

	public int getAttributesCount() {
		return attributesCount;
	}

	public static void main(String[] args) throws IOException {
//		File                classFile  = new File("/Users/yz/IdeaProjects/Servers/TW_7_2020-01-16/lib/tongweb/com/tongweb/tomee/catalina/event/AfterApplicationCreated.class");
//		ClassByteCodeParser codeParser = new ClassByteCodeParser();
//
//		codeParser.parseByteCode(new FileInputStream(classFile));

		Collection<File> files = FileUtils.listFiles(new File("/Users/yz/IdeaProjects/Servers/TW_7_2020-01-16/lib/tongweb"), new String[]{"class"}, true);

		for (File classFile : files) {
			System.out.println(classFile);
			ClassByteCodeParser codeParser = new ClassByteCodeParser();

			codeParser.parseByteCode(new FileInputStream(classFile));
			System.out.println(JSON.toJSONString(codeParser));
		}
	}

}