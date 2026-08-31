package com.whisper.habitat.compiler

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunction
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Visibility
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Habitat 注解处理器.
 *
 * 扫描 app 模块中参与 Habitat 的 RoomDatabase, 并生成 Dao Provider 与总 Registry.
 *
 * @aegis 保护注解校验, 生成 Provider/Registry ABI, 命名和增量依赖语义.
 * @aegis-audit 2026-08-31 | whisper | 经授权支持继承 Dao accessor 的收集、override 去重和增量依赖.
 * @aegis-audit 2026-08-31 | whisper | 经授权补全生成代码可访问性与调用形态校验.
 * @aegis-audit 2026-08-31 | whisper | 经授权消除实例入口级联误报并补全重复归属定位.
 * @aegis-audit 2026-08-31 | whisper | 经授权支持 Dao qualifier 解析、冲突校验和多数据库绑定生成.
 * @aegis-audit 2026-08-31 | whisper | 经授权移除阻碍同一 Dao 跨库绑定的 Entity 唯一归属限制.
 *
 * @author whisper
 * @since 2026/07/27
 */
class HabitatSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String>,
) : SymbolProcessor {

    /**
     * 生成 Registry 使用的 Kotlin 包名.
     */
    private val registryPackage: String? = options[REGISTRY_PACKAGE_OPTION]
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private var registryPackageChecked: Boolean = false

    private var resolvedGeneratedPackage: String? = null

    private var generated: Boolean = false

    private var hasError: Boolean = false

    private var deferredDatabaseSymbols: List<KSClassDeclaration> = emptyList()

    private val databaseModels: MutableMap<String, HabitatDatabaseModel> = linkedMapOf()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val generatedPackage: String = resolveGeneratedPackage() ?: return emptyList()
        val databaseSymbols: List<KSClassDeclaration> = resolver
            .getSymbolsWithAnnotation(HABITAT_DATABASE)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        val validDatabaseSymbols: MutableList<KSClassDeclaration> = mutableListOf()
        val deferredSymbols: MutableList<KSClassDeclaration> = mutableListOf()
        databaseSymbols.forEach { declaration: KSClassDeclaration ->
            if (declaration.validate()) {
                validDatabaseSymbols += declaration
            } else {
                deferredSymbols += declaration
            }
        }
        deferredDatabaseSymbols = deferredSymbols.distinct()

        validDatabaseSymbols.forEach { declaration: KSClassDeclaration ->
            val databaseQualifiedName: String? = declaration.qualifiedName?.asString()
            if (databaseQualifiedName != null && databaseModels.containsKey(databaseQualifiedName)) {
                return@forEach
            }
            val databaseModel: HabitatDatabaseModel = parseDatabase(
                declaration = declaration,
                generatedPackage = generatedPackage,
                resolver = resolver,
            ) ?: return@forEach
            databaseModels[databaseModel.databaseQualifiedName] = databaseModel
        }

        return deferredSymbols
    }

    override fun finish() {
        if (generated) {
            return
        }
        generated = true
        val generatedPackage: String = resolveGeneratedPackage() ?: return
        if (hasError) {
            return
        }
        if (deferredDatabaseSymbols.isNotEmpty()) {
            reportDeferredDatabaseErrors()
            return
        }

        val models: List<HabitatDatabaseModel> = databaseModels.values.toList()
        if (models.isEmpty()) {
            logger.warn("No Habitat database was found. Generated empty Habitat registry.")
            writeRegistry(
                generatedPackage = generatedPackage,
                models = emptyList(),
                dependencies = Dependencies.ALL_FILES,
            )
            return
        }

        if (!validateDatabases(models)) {
            return
        }
        models.forEach { model: HabitatDatabaseModel ->
            writeProvider(model)
        }
        val registryDependencies: Dependencies = Dependencies(
            aggregating = true,
            sources = collectSourceFiles(models).toTypedArray(),
        )
        writeRegistry(
            generatedPackage = generatedPackage,
            models = models,
            dependencies = registryDependencies,
        )
    }

    private fun resolveGeneratedPackage(): String? {
        if (registryPackageChecked) {
            return resolvedGeneratedPackage
        }
        registryPackageChecked = true
        val generatedPackage: String? = registryPackage
        if (generatedPackage == null) {
            reportError("Missing KSP option '$REGISTRY_PACKAGE_OPTION'. Apply com.whisper.habitat to the app module.")
            return null
        }
        if (!PACKAGE_NAME_PATTERN.matches(generatedPackage)) {
            reportError("Invalid KSP option '$REGISTRY_PACKAGE_OPTION': $generatedPackage.")
            return null
        }
        resolvedGeneratedPackage = generatedPackage
        return generatedPackage
    }

    private fun parseDatabase(
        declaration: KSClassDeclaration,
        generatedPackage: String,
        resolver: Resolver,
    ): HabitatDatabaseModel? {
        val databaseQualifiedName: String = declaration.qualifiedName?.asString()
            ?: return logError(declaration, "Habitat database must have a qualified name.")
        if (!declaration.isAccessibleFromGeneratedProvider()) {
            return logError(
                declaration,
                "Habitat database $databaseQualifiedName and its containing declarations must be public or internal.",
            )
        }
        if (!declaration.hasAnnotation(ROOM_DATABASE)) {
            return logError(declaration, "Habitat database $databaseQualifiedName must be annotated with @Database.")
        }
        if (!declaration.extendsClass(ROOM_DATABASE_CLASS)) {
            return logError(
                declaration,
                "Habitat database $databaseQualifiedName must extend androidx.room.RoomDatabase.",
            )
        }

        val instanceAccessor: HabitatDatabaseInstanceAccessor = when (
            val result: HabitatDatabaseInstanceAccessorResult = findInstanceAccessor(declaration)
        ) {
            is HabitatDatabaseInstanceAccessorResult.Found -> result.accessor
            HabitatDatabaseInstanceAccessorResult.Invalid -> return null
            HabitatDatabaseInstanceAccessorResult.Missing -> {
                return logError(
                    declaration,
                    "Habitat database $databaseQualifiedName must declare a companion object property or function " +
                        "annotated with @HabitatDatabaseInstance.",
                )
            }
        }

        val daoMethods: List<HabitatDaoMethod> = collectDaoMethods(
            declaration = declaration,
            resolver = resolver,
        )
        return HabitatDatabaseModel(
            databaseQualifiedName = databaseQualifiedName,
            databaseClassName = ClassName.bestGuess(databaseQualifiedName),
            providerClassName = createProviderClassName(
                generatedPackage = generatedPackage,
                databaseQualifiedName = databaseQualifiedName,
            ),
            instanceAccessor = instanceAccessor,
            daoMethods = daoMethods,
            declaration = declaration,
            sourceFile = declaration.containingFile,
        )
    }

    private fun findInstanceAccessor(
        declaration: KSClassDeclaration,
    ): HabitatDatabaseInstanceAccessorResult {
        val companionObject: KSClassDeclaration = declaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull(KSClassDeclaration::isCompanionObject)
            ?: return HabitatDatabaseInstanceAccessorResult.Missing
        val instanceProperties: List<KSPropertyDeclaration> = companionObject.declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .filter { property: KSPropertyDeclaration ->
                property.hasAnnotation(HABITAT_DATABASE_INSTANCE)
            }
            .toList()
        val instanceFunctions: List<KSFunctionDeclaration> = companionObject.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { function: KSFunctionDeclaration ->
                function.hasAnnotation(HABITAT_DATABASE_INSTANCE)
            }
            .toList()
        val instanceEntryCount: Int = instanceProperties.size + instanceFunctions.size
        if (instanceEntryCount > 1) {
            reportError(
                declaration,
                "Only one @HabitatDatabaseInstance property or function is allowed in " +
                    "${declaration.qualifiedName?.asString()}.",
            )
            return HabitatDatabaseInstanceAccessorResult.Invalid
        }
        val property: KSPropertyDeclaration? = instanceProperties.firstOrNull()
        if (property != null) {
            val accessor: HabitatDatabaseInstanceAccessor = parseInstanceProperty(property, declaration)
                ?: return HabitatDatabaseInstanceAccessorResult.Invalid
            return HabitatDatabaseInstanceAccessorResult.Found(accessor)
        }
        val function: KSFunctionDeclaration = instanceFunctions.firstOrNull()
            ?: return HabitatDatabaseInstanceAccessorResult.Missing
        val accessor: HabitatDatabaseInstanceAccessor = parseInstanceFunction(function, declaration)
            ?: return HabitatDatabaseInstanceAccessorResult.Invalid
        return HabitatDatabaseInstanceAccessorResult.Found(accessor)
    }

    private fun parseInstanceProperty(
        property: KSPropertyDeclaration,
        declaration: KSClassDeclaration,
    ): HabitatDatabaseInstanceAccessor? {
        if (property.extensionReceiver != null) {
            reportError(property, "@HabitatDatabaseInstance property must not have an extension receiver.")
            return null
        }
        val propertyType: KSType = property.type.resolve()
        val propertyTypeName: String? = propertyType.declaration.qualifiedName?.asString()
        val databaseTypeName: String? = declaration.qualifiedName?.asString()
        if (propertyType.nullability == Nullability.NULLABLE) {
            reportError(
                property,
                "@HabitatDatabaseInstance property must return a non-null $databaseTypeName.",
            )
            return null
        }
        if (propertyTypeName != databaseTypeName) {
            reportError(
                property,
                "@HabitatDatabaseInstance property must return $databaseTypeName.",
            )
            return null
        }
        if (!property.isAccessibleFromGeneratedProvider()) {
            reportError(
                property,
                "@HabitatDatabaseInstance property and its containing declarations must be public or internal.",
            )
            return null
        }
        return HabitatDatabaseInstanceAccessor(
            memberName = property.simpleName.asString(),
            kind = HabitatDatabaseInstanceAccessorKind.PROPERTY,
        )
    }

    private fun parseInstanceFunction(
        function: KSFunctionDeclaration,
        declaration: KSClassDeclaration,
    ): HabitatDatabaseInstanceAccessor? {
        val databaseTypeName: String? = declaration.qualifiedName?.asString()
        if (function.parameters.isNotEmpty()) {
            reportError(function, "@HabitatDatabaseInstance function must not declare parameters.")
            return null
        }
        if (function.extensionReceiver != null) {
            reportError(function, "@HabitatDatabaseInstance function must not have an extension receiver.")
            return null
        }
        if (Modifier.SUSPEND in function.modifiers) {
            reportError(function, "@HabitatDatabaseInstance function must not be suspend.")
            return null
        }
        if (function.typeParameters.isNotEmpty()) {
            reportError(function, "@HabitatDatabaseInstance function must not declare type parameters.")
            return null
        }
        val returnType: KSType = function.returnType?.resolve()
            ?: return logError(function, "@HabitatDatabaseInstance function must return $databaseTypeName.")
        val returnTypeName: String? = returnType.declaration.qualifiedName?.asString()
        if (returnType.nullability == Nullability.NULLABLE) {
            reportError(
                function,
                "@HabitatDatabaseInstance function must return a non-null $databaseTypeName.",
            )
            return null
        }
        if (returnTypeName != databaseTypeName) {
            reportError(function, "@HabitatDatabaseInstance function must return $databaseTypeName.")
            return null
        }
        if (!function.isAccessibleFromGeneratedProvider()) {
            reportError(
                function,
                "@HabitatDatabaseInstance function and its containing declarations must be public or internal.",
            )
            return null
        }
        return HabitatDatabaseInstanceAccessor(
            memberName = function.simpleName.asString(),
            kind = HabitatDatabaseInstanceAccessorKind.FUNCTION,
        )
    }

    private fun collectDaoMethods(
        declaration: KSClassDeclaration,
        resolver: Resolver,
    ): List<HabitatDaoMethod> {
        val databaseType: KSType = declaration.asStarProjectedType()
        val daoFunctions: List<HabitatDaoAccessorCandidate> = declaration.getAllFunctions()
            .mapNotNull { function: KSFunctionDeclaration ->
                function.toDaoAccessorCandidate(databaseType)
            }
            .toList()
        return daoFunctions
            .filterNot { function: HabitatDaoAccessorCandidate ->
                daoFunctions.any { candidate: HabitatDaoAccessorCandidate ->
                    candidate.function != function.function && resolver.overrides(
                        overrider = candidate.function,
                        overridee = function.function,
                        containingClass = declaration,
                    )
                }
            }
            .groupBy(HabitatDaoAccessorCandidate::signature)
            .values
            .mapNotNull { functions: List<HabitatDaoAccessorCandidate> ->
                parseDaoMethod(
                    function = functions.first(),
                    accessorSourceFiles = functions.mapNotNull { candidate: HabitatDaoAccessorCandidate ->
                        candidate.function.containingFile
                    },
                )
            }
    }

    private fun KSFunctionDeclaration.toDaoAccessorCandidate(
        databaseType: KSType,
    ): HabitatDaoAccessorCandidate? {
        if (!isAbstract) {
            return null
        }
        val memberFunction: KSFunction = asMemberOf(databaseType)
        val returnType: KSType = memberFunction.returnType
            ?.takeUnless { type: KSType -> memberFunction.isError || type.isError }
            ?: return null
        val returnDeclaration: KSDeclaration = returnType.declaration
        if (!returnDeclaration.hasAnnotation(ROOM_DAO)) {
            return null
        }
        return HabitatDaoAccessorCandidate(
            function = this,
            memberFunction = memberFunction,
            returnType = returnType,
            signature = HabitatDaoAccessorSignature(
                methodName = simpleName.asString(),
                receiverTypeName = memberFunction.extensionReceiverType
                    ?.declaration
                    ?.qualifiedName
                    ?.asString(),
                parameterTypeNames = memberFunction.parameterTypes.map { type: KSType? ->
                    type?.declaration?.qualifiedName?.asString()
                },
                returnTypeName = returnDeclaration.qualifiedName?.asString(),
            ),
        )
    }

    private fun parseDaoMethod(
        function: HabitatDaoAccessorCandidate,
        accessorSourceFiles: List<KSFile>,
    ): HabitatDaoMethod? {
        val declaration: KSFunctionDeclaration = function.function
        val methodName: String = declaration.simpleName.asString()
        if (declaration.parameters.isNotEmpty()) {
            return logError(declaration, "Habitat Dao accessor $methodName must not declare parameters.")
        }
        if (function.memberFunction.extensionReceiverType != null) {
            return logError(declaration, "Habitat Dao accessor $methodName must not have an extension receiver.")
        }
        if (Modifier.SUSPEND in declaration.modifiers) {
            return logError(declaration, "Habitat Dao accessor $methodName must not be suspend.")
        }
        if (declaration.typeParameters.isNotEmpty()) {
            return logError(declaration, "Habitat Dao accessor $methodName must not declare type parameters.")
        }
        val returnType: KSType = function.returnType
        if (returnType.nullability == Nullability.NULLABLE) {
            return logError(declaration, "Habitat Dao accessor $methodName must return a non-null Dao.")
        }
        if (!declaration.isAccessibleFromGeneratedProvider()) {
            return logError(
                declaration,
                "Habitat Dao accessor $methodName and its containing declarations must be public or internal.",
            )
        }
        val daoQualifiedName: String = returnType.declaration.qualifiedName?.asString()
            ?: return logError(declaration, "Habitat Dao return type must have a qualified name.")
        if (!returnType.declaration.isAccessibleFromGeneratedProvider()) {
            return logError(
                declaration,
                "Habitat Dao return type $daoQualifiedName and its containing declarations must be public or internal.",
            )
        }
        val bindingAnnotation: KSAnnotation? = declaration.findAnnotation(HABITAT_DAO_BINDING)
        val qualifier: String? = if (bindingAnnotation == null) {
            null
        } else {
            val value: String? = bindingAnnotation.arguments
                .firstOrNull { argument: KSValueArgument -> argument.name?.asString() == "value" }
                ?.value as? String
            if (value == null) {
                return logError(declaration, "@HabitatDaoBinding must declare a String qualifier.")
            }
            if (value.isBlank()) {
                return logError(declaration, "@HabitatDaoBinding qualifier must not be blank.")
            }
            value
        }
        return HabitatDaoMethod(
            methodName = methodName,
            daoClassName = ClassName.bestGuess(daoQualifiedName),
            qualifier = qualifier,
            declaration = declaration,
            sourceFiles = accessorSourceFiles
                .plus(listOfNotNull(returnType.declaration.containingFile))
                .distinctBy(KSFile::filePath),
        )
    }

    private fun validateDatabases(models: List<HabitatDatabaseModel>): Boolean {
        val providerConflicts: Map<String, List<HabitatDatabaseModel>> = findDatabaseOwnershipConflicts(
            models = models,
            ownershipKeys = { model: HabitatDatabaseModel ->
                listOf(model.providerClassName.canonicalName)
            },
        )
        reportDatabaseOwnershipConflicts(providerConflicts) { providerName: String, databaseNames: String ->
            "Habitat provider $providerName is generated by multiple Habitat databases. " +
                "Conflicting databases: $databaseNames."
        }

        val daoBindingsValid: Boolean = validateDaoBindings(models)

        return providerConflicts.isEmpty() && daoBindingsValid
    }

    private fun validateDaoBindings(models: List<HabitatDatabaseModel>): Boolean {
        var isValid: Boolean = true
        val bindingsByDao: Map<String, List<HabitatDaoBindingOwner>> = models
            .flatMap { model: HabitatDatabaseModel ->
                model.daoMethods.map { method: HabitatDaoMethod ->
                    HabitatDaoBindingOwner(database = model, method = method)
                }
            }
            .groupBy { owner: HabitatDaoBindingOwner -> owner.method.daoClassName.canonicalName }
        bindingsByDao.forEach { (daoName: String, owners: List<HabitatDaoBindingOwner>) ->
            if (owners.size <= 1) {
                return@forEach
            }
            val accessorNames: String = owners
                .map(HabitatDaoBindingOwner::displayName)
                .sorted()
                .joinToString()
            if (owners.any { owner: HabitatDaoBindingOwner -> owner.method.qualifier == null }) {
                isValid = false
                val message: String =
                    "Dao $daoName has multiple Habitat accessors. Every accessor must declare " +
                        "@HabitatDaoBinding with a unique qualifier. Conflicting accessors: $accessorNames."
                owners.forEach { owner: HabitatDaoBindingOwner ->
                    reportError(owner.method.declaration, message)
                }
                return@forEach
            }
            owners
                .groupBy { owner: HabitatDaoBindingOwner -> checkNotNull(owner.method.qualifier) }
                .filterValues { qualifierOwners: List<HabitatDaoBindingOwner> -> qualifierOwners.size > 1 }
                .forEach { (qualifier: String, qualifierOwners: List<HabitatDaoBindingOwner>) ->
                    isValid = false
                    val conflictingAccessors: String = qualifierOwners
                        .map(HabitatDaoBindingOwner::displayName)
                        .sorted()
                        .joinToString()
                    val message: String =
                        "Dao $daoName uses duplicate Habitat qualifier '$qualifier'. " +
                            "Conflicting accessors: $conflictingAccessors."
                    qualifierOwners.forEach { owner: HabitatDaoBindingOwner ->
                        reportError(owner.method.declaration, message)
                    }
                }
        }
        return isValid
    }

    private fun findDatabaseOwnershipConflicts(
        models: List<HabitatDatabaseModel>,
        ownershipKeys: (HabitatDatabaseModel) -> List<String>,
    ): Map<String, List<HabitatDatabaseModel>> {
        return models
            .flatMap { model: HabitatDatabaseModel ->
                ownershipKeys(model)
                    .distinct()
                    .map { key: String -> key to model }
            }
            .groupBy(
                keySelector = { entry: Pair<String, HabitatDatabaseModel> -> entry.first },
                valueTransform = { entry: Pair<String, HabitatDatabaseModel> -> entry.second },
            )
            .mapValues { entry: Map.Entry<String, List<HabitatDatabaseModel>> ->
                entry.value
                    .distinctBy(HabitatDatabaseModel::databaseQualifiedName)
                    .sortedBy(HabitatDatabaseModel::databaseQualifiedName)
            }
            .filterValues { owners: List<HabitatDatabaseModel> -> owners.size > 1 }
            .toSortedMap()
    }

    private fun reportDatabaseOwnershipConflicts(
        conflicts: Map<String, List<HabitatDatabaseModel>>,
        message: (conflictName: String, databaseNames: String) -> String,
    ) {
        conflicts.forEach { (conflictName: String, owners: List<HabitatDatabaseModel>) ->
            val databaseNames: String = owners.joinToString { model: HabitatDatabaseModel ->
                model.databaseQualifiedName
            }
            owners.forEach { model: HabitatDatabaseModel ->
                reportError(model.declaration, message(conflictName, databaseNames))
            }
        }
    }

    private fun createProviderClassName(
        generatedPackage: String,
        databaseQualifiedName: String,
    ): ClassName {
        val databaseClassName: ClassName = ClassName.bestGuess(databaseQualifiedName)
        val providerPackage: String = listOf(
            generatedPackage,
            PROVIDER_PACKAGE_SEGMENT,
            databaseClassName.packageName,
        )
            .filter(String::isNotBlank)
            .joinToString(separator = ".")
        val providerSimpleName: String = databaseClassName.simpleNames
            .joinToString(separator = "_", postfix = PROVIDER_CLASS_SUFFIX)
        return ClassName(providerPackage, providerSimpleName)
    }

    private fun writeProvider(model: HabitatDatabaseModel) {
        val factoryType: LambdaTypeName = LambdaTypeName.get(returnType = ANY_CLASS_NAME)
        val qualifiedFactoryMapType: ParameterizedTypeName = MAP_CLASS_NAME.parameterizedBy(
            STRING_CLASS_NAME.copy(nullable = true),
            factoryType,
        )
        val factoryMapType: ParameterizedTypeName = MAP_CLASS_NAME.parameterizedBy(
            KCLASS_CLASS_NAME.parameterizedBy(STAR),
            qualifiedFactoryMapType,
        )
        val providerType: TypeSpec = TypeSpec.classBuilder(model.providerClassName)
            .addSuperinterface(HABITAT_DAO_PROVIDER_CLASS_NAME)
            .addProperty(
                PropertySpec.builder("daoFactories", factoryMapType)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(createDaoFactoriesInitializer(model))
                    .build()
            )
            .build()

        val fileSpec: FileSpec = FileSpec.builder(model.providerClassName.packageName, model.providerClassName.simpleName)
            .addType(providerType)
            .build()
        val dependencies: Dependencies = Dependencies(
            aggregating = false,
            sources = model.providerSourceFiles().toTypedArray(),
        )
        writeFile(fileSpec, dependencies)
    }

    private fun createDaoFactoriesInitializer(model: HabitatDatabaseModel): CodeBlock {
        val builder: CodeBlock.Builder = CodeBlock.builder()
            .add("mapOf(\n")
            .indent()
        model.daoMethods
            .groupBy { method: HabitatDaoMethod -> method.daoClassName.canonicalName }
            .values
            .forEach { methods: List<HabitatDaoMethod> ->
                builder.add("%T::class to mapOf(\n", methods.first().daoClassName)
                builder.indent()
                methods.forEach { method: HabitatDaoMethod ->
                    if (method.qualifier == null) {
                        builder.add("null")
                    } else {
                        builder.add("%S", method.qualifier)
                    }
                    // 使用 lambda 延迟读取数据库实例, 避免 Provider 初始化时抢先触发数据库单例读取.
                    when (model.instanceAccessor.kind) {
                        HabitatDatabaseInstanceAccessorKind.PROPERTY -> {
                            builder.add(
                                " to { %T.%N.%N() },\n",
                                model.databaseClassName,
                                model.instanceAccessor.memberName,
                                method.methodName,
                            )
                        }
                        HabitatDatabaseInstanceAccessorKind.FUNCTION -> {
                            builder.add(
                                " to { %T.%N().%N() },\n",
                                model.databaseClassName,
                                model.instanceAccessor.memberName,
                                method.methodName,
                            )
                        }
                    }
                }
                builder.unindent()
                builder.add("),\n")
            }
        return builder
            .unindent()
            .add(")")
            .build()
    }

    private fun writeRegistry(
        generatedPackage: String,
        models: List<HabitatDatabaseModel>,
        dependencies: Dependencies,
    ) {
        val providerListType: ParameterizedTypeName =
            LIST_CLASS_NAME.parameterizedBy(HABITAT_DAO_PROVIDER_CLASS_NAME)
        val registryType: TypeSpec = TypeSpec.classBuilder(GENERATED_REGISTRY_SIMPLE_NAME)
            .addSuperinterface(HABITAT_REGISTRY_CLASS_NAME)
            .addFunction(
                FunSpec.builder("providers")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(providerListType)
                    .apply {
                        addCode("return listOf(\n")
                        models.forEach { model: HabitatDatabaseModel ->
                            addCode("    %T(),\n", model.providerClassName)
                        }
                        addCode(")\n")
                    }
                    .build()
            )
            .build()
        val fileSpec: FileSpec = FileSpec.builder(generatedPackage, GENERATED_REGISTRY_SIMPLE_NAME)
            .addType(registryType)
            .build()
        writeFile(fileSpec, dependencies)
    }

    private fun collectSourceFiles(models: List<HabitatDatabaseModel>): List<KSFile> {
        return models
            .flatMap(HabitatDatabaseModel::allSourceFiles)
            .distinctBy(KSFile::filePath)
    }

    private fun KSDeclaration.hasAnnotation(qualifiedName: String): Boolean {
        return findAnnotation(qualifiedName) != null
    }

    private fun KSDeclaration.findAnnotation(qualifiedName: String): KSAnnotation? {
        return annotations.firstOrNull { annotation: KSAnnotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
        }
    }

    private fun KSDeclaration.isAccessibleFromGeneratedProvider(): Boolean {
        var currentDeclaration: KSDeclaration? = this
        while (currentDeclaration != null) {
            val visibility: Visibility = currentDeclaration.getVisibility()
            if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
                return false
            }
            currentDeclaration = currentDeclaration.parentDeclaration
        }
        return true
    }

    private fun KSClassDeclaration.extendsClass(qualifiedName: String): Boolean {
        val visited: MutableSet<String> = mutableSetOf()
        return extendsClass(qualifiedName, visited)
    }

    private fun KSClassDeclaration.extendsClass(
        qualifiedName: String,
        visited: MutableSet<String>,
    ): Boolean {
        val currentName: String = this.qualifiedName?.asString() ?: return false
        if (!visited.add(currentName)) {
            return false
        }
        return superTypes
            .mapNotNull { typeReference -> typeReference.resolve().declaration as? KSClassDeclaration }
            .any { superClass: KSClassDeclaration ->
                superClass.qualifiedName?.asString() == qualifiedName || superClass.extendsClass(
                    qualifiedName,
                    visited,
                )
            }
    }

    private fun writeFile(fileSpec: FileSpec, dependencies: Dependencies) {
        val file: OutputStream = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = fileSpec.packageName,
            fileName = fileSpec.name,
        )
        OutputStreamWriter(file, StandardCharsets.UTF_8).use(fileSpec::writeTo)
    }

    private fun <T> logError(symbol: KSAnnotated, message: String): T? {
        reportError(symbol, message)
        return null
    }

    private fun reportError(message: String) {
        hasError = true
        logger.error(message)
    }

    private fun reportDeferredDatabaseErrors() {
        deferredDatabaseSymbols.forEach { declaration: KSClassDeclaration ->
            val qualifiedName: String = declaration.qualifiedName?.asString()
                ?: declaration.simpleName.asString()
            reportError(
                declaration,
                "Habitat cannot process '$qualifiedName' because referenced symbols remain " +
                    "unresolved after all KSP rounds. Ensure its generated dependencies are " +
                    "available in the same compilation."
            )
        }
    }

    private fun reportError(symbol: KSAnnotated, message: String) {
        hasError = true
        logger.error(message, symbol)
    }

    /**
     * Habitat 数据库生成模型.
     */
    private data class HabitatDatabaseModel(
        val databaseQualifiedName: String,
        val databaseClassName: ClassName,
        val providerClassName: ClassName,
        val instanceAccessor: HabitatDatabaseInstanceAccessor,
        val daoMethods: List<HabitatDaoMethod>,
        val declaration: KSClassDeclaration,
        val sourceFile: KSFile?,
    ) {

        /**
         * Provider 生成依赖的源码文件.
         */
        fun providerSourceFiles(): List<KSFile> {
            return listOfNotNull(sourceFile)
                .plus(daoMethods.flatMap(HabitatDaoMethod::sourceFiles))
                .distinctBy(KSFile::filePath)
        }

        /**
         * Registry 与校验依赖的源码文件.
         */
        fun allSourceFiles(): List<KSFile> {
            return providerSourceFiles()
        }
    }

    /**
     * Habitat Dao 方法生成模型.
     */
    private data class HabitatDaoMethod(
        val methodName: String,
        val daoClassName: ClassName,
        val qualifier: String?,
        val declaration: KSFunctionDeclaration,
        val sourceFiles: List<KSFile>,
    )

    /**
     * Habitat Dao 绑定及其数据库归属.
     */
    private data class HabitatDaoBindingOwner(
        val database: HabitatDatabaseModel,
        val method: HabitatDaoMethod,
    ) {

        fun displayName(): String {
            return "${database.databaseQualifiedName}.${method.methodName}"
        }
    }

    /**
     * Habitat Dao accessor 解析候选.
     */
    private data class HabitatDaoAccessorCandidate(
        val function: KSFunctionDeclaration,
        val memberFunction: KSFunction,
        val returnType: KSType,
        val signature: HabitatDaoAccessorSignature,
    )

    /**
     * Habitat Dao accessor 去重签名.
     */
    private data class HabitatDaoAccessorSignature(
        val methodName: String,
        val receiverTypeName: String?,
        val parameterTypeNames: List<String?>,
        val returnTypeName: String?,
    )

    /**
     * Habitat 数据库实例入口生成模型.
     */
    private data class HabitatDatabaseInstanceAccessor(
        val memberName: String,
        val kind: HabitatDatabaseInstanceAccessorKind,
    )

    /**
     * Habitat 数据库实例入口解析结果.
     */
    private sealed interface HabitatDatabaseInstanceAccessorResult {

        data class Found(
            val accessor: HabitatDatabaseInstanceAccessor,
        ) : HabitatDatabaseInstanceAccessorResult

        data object Missing : HabitatDatabaseInstanceAccessorResult

        data object Invalid : HabitatDatabaseInstanceAccessorResult
    }

    /**
     * Habitat 数据库实例入口类型.
     */
    private enum class HabitatDatabaseInstanceAccessorKind {
        PROPERTY,
        FUNCTION,
    }

    /**
     * Habitat 处理器常量.
     */
    private companion object {

        // ---------------------------------------------------------------------
        // Compiler 内部实现常量.
        // ---------------------------------------------------------------------

        // 以下值只用于当前处理器的校验和源码生成, 不属于跨模块协议.

        /**
         * 生成 Provider 包名使用的固定路径段.
         */
        private const val PROVIDER_PACKAGE_SEGMENT: String = "providers"

        /**
         * 生成 Provider 类名使用的固定后缀.
         */
        private const val PROVIDER_CLASS_SUFFIX: String = "HabitatDaoProvider"

        /**
         * Kotlin Any 类型.
         */
        private val ANY_CLASS_NAME: ClassName = ClassName("kotlin", "Any")

        /**
         * Kotlin KClass 类型.
         */
        private val KCLASS_CLASS_NAME: ClassName = ClassName("kotlin.reflect", "KClass")

        /**
         * Kotlin String 类型.
         */
        private val STRING_CLASS_NAME: ClassName = ClassName("kotlin", "String")

        /**
         * Kotlin Map 类型.
         */
        private val MAP_CLASS_NAME: ClassName = ClassName("kotlin.collections", "Map")

        /**
         * Kotlin List 类型.
         */
        private val LIST_CLASS_NAME: ClassName = ClassName("kotlin.collections", "List")

        /**
         * 生成 Registry 包名的 Kotlin 包名格式.
         */
        private val PACKAGE_NAME_PATTERN: Regex =
            Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

        // ---------------------------------------------------------------------
        // Habitat 外部协议常量.
        // ---------------------------------------------------------------------

        // 以下值需要与 annotation、Gradle 插件、Runtime 或 Room API 中的对应协议保持一致.

        /**
         * HabitatDatabase 注解的全限定类名.
         */
        private const val HABITAT_DATABASE: String =
            "com.whisper.habitat.runtime.annotation.HabitatDatabase"

        /**
         * HabitatDatabaseInstance 注解的全限定类名.
         */
        private const val HABITAT_DATABASE_INSTANCE: String =
            "com.whisper.habitat.runtime.annotation.HabitatDatabaseInstance"

        /**
         * HabitatDaoBinding 注解的全限定类名.
         */
        private const val HABITAT_DAO_BINDING: String =
            "com.whisper.habitat.runtime.annotation.HabitatDaoBinding"

        /**
         * Room Database 注解的全限定类名.
         */
        private const val ROOM_DATABASE: String = "androidx.room.Database"

        /**
         * RoomDatabase 基类的全限定类名.
         */
        private const val ROOM_DATABASE_CLASS: String = "androidx.room.RoomDatabase"

        /**
         * Room Dao 注解的全限定类名.
         */
        private const val ROOM_DAO: String = "androidx.room.Dao"

        /**
         * Gradle 插件传递 Registry 包名使用的 KSP 参数名.
         */
        private const val REGISTRY_PACKAGE_OPTION: String = "habitat.registryPackage"

        /**
         * 每个模块生成的 Registry 类名.
         */
        private const val GENERATED_REGISTRY_SIMPLE_NAME: String = "GeneratedHabitatRegistry"

        /**
         * Dao Provider 接口的 KotlinPoet 类型.
         */
        private val HABITAT_DAO_PROVIDER_CLASS_NAME: ClassName =
            ClassName("com.whisper.habitat.runtime.registry", "HabitatDaoProvider")

        /**
         * Registry 接口的 KotlinPoet 类型.
         */
        private val HABITAT_REGISTRY_CLASS_NAME: ClassName =
            ClassName("com.whisper.habitat.runtime.registry", "HabitatRegistry")
    }
}
