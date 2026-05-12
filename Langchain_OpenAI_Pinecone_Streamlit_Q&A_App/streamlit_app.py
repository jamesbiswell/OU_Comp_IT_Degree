# using the terminal, run this file like so:
# streamlit run streamlit_app.py

# .env files are not included in the repository for security reasons (as specified
# in the .gitignore file), so create your own .env file with the following content:
# OPENAI_API_KEY=""
# PINECONE_API_KEY=""
# LANGCHAIN_API_KEY=""
# LANGCHAIN_TRACING_V2="false"
from dotenv import load_dotenv, find_dotenv
load_dotenv(find_dotenv(), override=True)

import streamlit as st

# Import LangChain/OpenAI/Pinecone libraries
from langchain_classic.chains.retrieval_qa.base import RetrievalQA
from langchain_openai import ChatOpenAI
from langchain_community.vectorstores import Pinecone as LangChainPinecone
from langchain_openai import OpenAIEmbeddings
from pinecone import Pinecone

index_name = "ou-comp-it-degree"
namespace = "ou-comp-it-degree"


@st.cache_resource
def load_vector_store():
    embeddings = OpenAIEmbeddings(
        model="text-embedding-3-small",
        dimensions=1536,
    )

    pc = Pinecone()
    index = pc.Index(index_name)

    vector_store = LangChainPinecone(
        index=index,
        embedding=embeddings,
        text_key="text",
        namespace=namespace,
    )

    return vector_store


@st.cache_resource
def load_qa_chain():
    vector_store = load_vector_store()

    llm = ChatOpenAI(
        model="gpt-4o-mini",
        temperature=0,
    )

    retriever = vector_store.as_retriever(
        search_type="similarity",
        search_kwargs={"k": 3},
    )

    qa_chain = RetrievalQA.from_chain_type(
        llm=llm,
        chain_type="stuff",
        retriever=retriever,
        return_source_documents=True,
    )

    return qa_chain


def ask_and_get_answer(q):
    qa_chain = load_qa_chain()
    return qa_chain.invoke({"query": q})


vector_store = load_vector_store()

st.set_page_config(
    page_title="RAG Q&A App",
    page_icon="",
    layout="wide",
)

st.title("RAG Q&A App")
st.write("Ask questions and get answers (from the provided context only).")

if "history" not in st.session_state:
    st.session_state.history = []

with st.form("question_form", clear_on_submit=True):
    question = st.text_input("Enter your question:")
    submitted = st.form_submit_button("Ask")

if submitted:
    if not question.strip():
        st.warning("Please enter a question.")
    else:
        with st.spinner("Thinking..."):
            q = question + " Answer from the provided context only."
            answer = ask_and_get_answer(q)

        st.session_state.history.append(answer)

total_questions = len(st.session_state.history)

for display_index, answer in enumerate(reversed(st.session_state.history), start=1):
    question_number = total_questions - display_index + 1

    st.divider()

    st.subheader(f"Question {question_number}")
    st.write(answer["query"])

    st.subheader("Answer")
    st.write(answer["result"])

    st.subheader("Reference(s)")

    source_documents = answer.get("source_documents", [])

    if not source_documents:
        st.info("No references returned.")
    else:
        for index, doc in enumerate(source_documents, start=1):
            source = doc.metadata.get("source", "Unknown document")
            page_label = doc.metadata.get("page_label")
            page = doc.metadata.get("page")

            with st.expander(f"Reference {index}: {source}"):
                st.write(f"**Document:** {source}")
                st.write(f"**Page label:** {page_label}")
                st.write(f"**Page index:** {page}")