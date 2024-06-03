/*
* Copyright 2017 Alexander Koval
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package company.evo.opensearch.index.mapper.external

import company.evo.opensearch.indices.ExternalFileService
import company.evo.opensearch.plugin.mapper.ExternalFileMapperPlugin
import company.evo.persistent.hashmap.straight.StraightHashMapEnv
import company.evo.persistent.hashmap.straight.StraightHashMapType_Int_Float

import org.opensearch.common.compress.CompressedXContent
import org.opensearch.common.xcontent.XContentFactory.jsonBuilder
import org.opensearch.common.xcontent.XContentType
import org.opensearch.core.common.bytes.BytesReference
import org.opensearch.core.xcontent.XContentBuilder
import org.opensearch.index.IndexService
import org.opensearch.index.mapper.MapperParsingException
import org.opensearch.index.mapper.MapperService
import org.opensearch.index.mapper.SourceToParse
import org.opensearch.plugins.Plugin
import org.opensearch.test.OpenSearchSingleNodeTestCase
import org.opensearch.test.InternalSettingsPlugin

import org.hamcrest.Matchers.containsString

import org.junit.Before
import org.junit.Ignore

import java.nio.file.Files
import java.util.Arrays

inline fun XContentBuilder.obj(
        name: String? = null,
        block: XContentBuilder.() -> Unit
): XContentBuilder {
    if (name != null) startObject(name) else startObject()
    block()
    endObject()
    return this
}

class ExternalFieldMapperParserTests : OpenSearchSingleNodeTestCase() {

    lateinit var indexService: IndexService
    lateinit var mapperService: MapperService

    @Before
    fun setup() {
        indexService = createIndex("test")
        mapperService = indexService.mapperService()
    }

    override fun getPlugins(): Collection<Class<out Plugin>> {
        return pluginList(
            InternalSettingsPlugin::class.java,
            ExternalFileMapperPlugin::class.java
        )
    }

    fun testDefaults() {
        val mapping = jsonBuilder().obj {
            obj("properties") {
                obj("id") {
                    field("type", "integer")
                }
                obj("ext_field") {
                    field("type", "external_file")
                    field("key_field", "id")
                    field("map_name", "test_ext_file")
                }
            }
        }

        val documentMapper = mapperService.parse(
            MapperService.SINGLE_MAPPING_NAME,
            CompressedXContent(BytesReference.bytes(mapping))
        )

        val parsedDoc = documentMapper.parse(
                SourceToParse(
                        "test", "1",
                        BytesReference.bytes(
                                jsonBuilder().obj {
                                    field("id", 1)
                                    field("ext_field", "value")
                                }
                        ),
                        XContentType.JSON
                )
        )
        val testExtFileFields = parsedDoc.rootDoc().getFields("ext_field")
        assertNotNull(testExtFileFields)
        assertEquals(Arrays.toString(testExtFileFields), 0, testExtFileFields.size)
        val idFields = parsedDoc.rootDoc().getFields("id")
        assertNotNull(idFields)
        assertEquals(Arrays.toString(idFields), 2, idFields.size)
    }

    // TODO find a way to check existing of the key_field when parsing mapping
    // fun testNonexistentIdKeyField() {
    //     val mapping = jsonBuilder().obj {
    //         obj("type") {
    //             obj("properties") {
    //                 obj("ext_field") {
    //                     field("type", "external_file")
    //                     field("key_field", "id")
    //                     field("map_name", "test_ext_file")
    //                 }
    //             }
    //         }
    //     }
    //     try {
    //         val documentMapper = mapperService.parse("type", CompressedXContent(BytesReference.bytes(mapping)), false)
    //         documentMapper.validate(indexService.indexSettings, true)
    //         fail("Expected a mapper parsing exception")
    //     } catch (e: MapperParsingException) {
    //         assertThat(e.message, containsString("[id] field not found"))
    //     }
    // }

    // fun testDocValuesNotAllowed() {
    //     val mapping = jsonBuilder().obj {
    //         obj("type") {
    //             obj("properties") {
    //                 obj("ext_field") {
    //                     field("type", "external_file")
    //                     field("key_field", "id")
    //                     field("map_name", "test_ext_file")
    //                     field("doc_values", false)
    //                 }
    //             }
    //         }
    //     }
    //     try {
    //         val documentMapper = mapperService.parse("type", CompressedXContent(BytesReference.bytes(mapping)), false)
    //         // documentMapper.validate(indexService.indexSettings, true)
    //         fail("Expected a mapper parsing exception")
    //     } catch (e: MapperParsingException) {
    //         assertThat(
    //             e.message,
    //             containsString("[doc_values] parameter cannot be modified for field [ext_field]")
    //         )
    //     }
    // }

    // @Ignore
    fun testStoredNotAllowed() {
        val mapping = jsonBuilder().obj {
            obj("type") {
                obj("properties") {
                    obj("ext_field") {
                        field("type", "external_file")
                        field("key_field", "id")
                        field("map_name", "test_ext_file")
                        field("stored", true)
                    }
                }
            }
        }
        try {
            mapperService.parse(
                MapperService.SINGLE_MAPPING_NAME,
                CompressedXContent(BytesReference.bytes(mapping))
            )
            fail("Expected a mapper parsing exception")
        } catch (e: MapperParsingException) {
            assertThat(
                e.message,
                containsString("!!!Root mapping definition has unsupported parameters:")
            )
        }
    }
}
