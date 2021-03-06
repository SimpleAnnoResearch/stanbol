/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.stanbol.enhancer.engines.entitylinking;

/**
 * Interface used by the {@link EntityhubLinkingEngine} to tokenize labels of
 * Entities suggested by the EntitySearcher
 *
 */
public interface LabelTokenizer {

    /**
     * Key used to configure the languages supported for tokenizing labels.
     * If not present the assumption is that the tokenizer supports all languages.
     */
    String SUPPORTED_LANUAGES = "enhancer.engines.entitylinking.labeltokenizer.languages";
    
    
    /**
     * Tokenizes the parsed label in the parsed language
     * @param label the label
     * @param language the language of the lable or <code>null</code> if
     * not known
     * @return the tokenized label
     */
    String[] tokenize(String label, String language);
    
}
