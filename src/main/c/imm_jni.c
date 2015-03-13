#include <windows.h>
#include "com_yogpc_gi_w32_JNIHandler.h"
static HWND hWnd = NULL;
static WNDPROC pWndProc = NULL;
static jobject gCLS = NULL;
#define JMID_RES 0
#define JMID_CAND 1
#define JMID_COMP 2
#define JMID_KILL 3
#define JMID_STAT 4
static jmethodID jms[5];
static void sendNullKeydown() {
	if (pWndProc == NULL || hWnd != GetForegroundWindow())
		return;
	INPUT in;
	in.type = INPUT_KEYBOARD;
	in.ki.wVk = VK_BROWSER_REFRESH;
	in.ki.wScan = MapVirtualKey(VK_BROWSER_REFRESH, 0);
	in.ki.dwFlags = KEYEVENTF_EXTENDEDKEY;
	in.ki.time = 0;
	in.ki.dwExtraInfo = 0;// FIXME GetMessageExtraInfo()
	SendInput(1, &in, sizeof(INPUT));
}
static JNIEnv *getJE(JavaVM **sr) {
	JavaVM *jv = NULL;
	jsize vms = 0;
	JNI_GetCreatedJavaVMs(&jv, 1, &vms);
	JNIEnv *je = NULL;
	jint ret = (*jv)->GetEnv(jv, (void **) &je, JNI_VERSION_1_6);
	if (ret == JNI_OK) return je;
	(*jv)->AttachCurrentThread(jv, (void **) &je, NULL);
	*sr = jv;
	return je;
}
static void pushResult() {
	HIMC  hIMC = ImmGetContext(hWnd);
	LONG  ssiz = ImmGetCompositionStringW(hIMC, GCS_RESULTSTR , NULL, 0);
	jchar *str = malloc(ssiz);
	      ssiz = ImmGetCompositionStringW(hIMC, GCS_RESULTSTR , str, ssiz);
	JavaVM *sr = NULL; JNIEnv *je = getJE(&sr);
	jstring js = (*je)->NewString(je, str, ssiz / sizeof(jchar));
	(*je)->CallStaticVoidMethod(je, gCLS, jms[JMID_RES], js);
	if (sr) (*sr)->DetachCurrentThread(sr);
	free(str);
	ImmReleaseContext(hWnd, hIMC);
	sendNullKeydown();// FIXME
}
static void pushComposition() {
	HIMC  hIMC = ImmGetContext(hWnd);
	LONG  size = ImmGetCompositionStringW(hIMC, GCS_COMPATTR, NULL, 0);
	jbyte *ret = malloc(size);
	      size = ImmGetCompositionStringW(hIMC, GCS_COMPATTR, ret, size);
	LONG  ssiz = ImmGetCompositionStringW(hIMC, GCS_COMPSTR , NULL, 0);
	jchar *str = malloc(ssiz);
	      ssiz = ImmGetCompositionStringW(hIMC, GCS_COMPSTR , str, ssiz);
	LONG   csr = ImmGetCompositionStringW(hIMC, GCS_CURSORPOS, NULL, 0);
	JavaVM *sr = NULL; JNIEnv *je = getJE(&sr);
	jbyteArray jba = (*je)->NewByteArray(je, size / sizeof(jbyte));
	(*je)->SetByteArrayRegion(je, jba, 0, size / sizeof(jbyte), ret);
	jcharArray jca = (*je)->NewCharArray(je, ssiz / sizeof(jchar));
	(*je)->SetCharArrayRegion(je, jca, 0, ssiz / sizeof(jchar), str);
	(*je)->CallStaticVoidMethod(je, gCLS, jms[JMID_COMP], jca, jba, csr);
	if (sr) (*sr)->DetachCurrentThread(sr);
	free(str);
	free(ret);
	ImmReleaseContext(hWnd, hIMC);
}
static void pushCandidate() {
	// FIXME ImmGetCandidateListCount
	HIMC  hIMC = ImmGetContext(hWnd);
	LONG  size = ImmGetCandidateListW(hIMC, 0, NULL, 0);
	LPCANDIDATELIST cndl = malloc(size);
	      size = ImmGetCandidateListW(hIMC, 0, cndl, size);
	JavaVM *sr = NULL; JNIEnv *je = getJE(&sr);
	jclass sc = (*je)->FindClass(je, "java/lang/String");
	jobjectArray jsa = (*je)->NewObjectArray(je, cndl->dwCount, sc, NULL);
	int i = 0;
	for (; i < cndl->dwCount; i++)
		(*je)->SetObjectArrayElement(je, jsa, i,
				(*je)->NewString(je, (void*)cndl + cndl->dwOffset[i],
				wcslen((void*)cndl + cndl->dwOffset[i])));
	(*je)->CallStaticVoidMethod(je, gCLS, jms[JMID_CAND], jsa,
			cndl->dwSelection, cndl->dwPageStart, cndl->dwPageSize);
	if (sr) (*sr)->DetachCurrentThread(sr);
	free(cndl);
	ImmReleaseContext(hWnd, hIMC);
}
static void pushClear(jmethodID jm, int c) {
	JavaVM *sr = NULL; JNIEnv *je = getJE(&sr);
	jvalue *joa = malloc(c * sizeof(jvalue));
	memset(joa, 0, c * sizeof(jvalue));
	(*je)->CallStaticVoidMethodA(je, gCLS, jm, joa);
	if (sr) (*sr)->DetachCurrentThread(sr);
	free(joa);
}
static void pushStatus() {
	HIMC hIMC = ImmGetContext(hWnd);
	JavaVM *sr = NULL; JNIEnv *je = getJE(&sr);
	(*je)->CallStaticVoidMethod(je, gCLS, jms[JMID_STAT],
			ImmGetOpenStatus(hIMC));
	if (sr) (*sr)->DetachCurrentThread(sr);
	ImmReleaseContext(hWnd, hIMC);
}
static void killIME() {
	JavaVM *sr = NULL; JNIEnv *je = getJE(&sr);
	jboolean jb = (*je)->CallStaticBooleanMethod(je, gCLS, jms[JMID_KILL]);
	if (sr) (*sr)->DetachCurrentThread(sr);
	if (!jb) return;
	HIMC hIMC = ImmGetContext(hWnd);
	ImmNotifyIME(hIMC, NI_COMPOSITIONSTR, CPS_CANCEL, 0);
	ImmReleaseContext(hWnd, hIMC);
}
LRESULT CALLBACK WndProc(HWND phWnd, UINT msg, WPARAM wp, LPARAM lp) {
	switch (msg) {
		case WM_IME_STARTCOMPOSITION:
			killIME();
		case WM_IME_CHAR:
			return S_OK;
		case WM_IME_COMPOSITION:
			if (lp & GCS_RESULTSTR)
				pushResult();
			else if (lp & GCS_COMPSTR || lp & GCS_COMPATTR
						|| lp & GCS_CURSORPOS)
				pushComposition();
			return S_OK;
		case WM_IME_ENDCOMPOSITION:
			pushClear(jms[JMID_COMP], 3);
			return S_OK;
		case WM_IME_NOTIFY:
			switch (wp) {
				case IMN_OPENCANDIDATE:
				case IMN_CHANGECANDIDATE:
					pushCandidate();
					return S_OK;
				case IMN_CLOSECANDIDATE:
					pushClear(jms[JMID_CAND], 4);
					return S_OK;
				case IMN_SETOPENSTATUS:
					pushStatus();
					return S_OK;
			}
			break;
		case WM_INPUTLANGCHANGE:
			pushStatus();
			return S_OK;
		case WM_IME_SETCONTEXT:
			return DefWindowProc(phWnd, msg, wp, 0);
	}
	return CallWindowProc(pWndProc, phWnd, msg, wp, lp);
}
static void initGlobal(JNIEnv *je, jclass jc) {
	gCLS = (*je)->NewGlobalRef(je, jc);
	jms[JMID_RES ] = (*je)->GetStaticMethodID(je, gCLS,
			"cbResult", "(Ljava/lang/String;)V");
	jms[JMID_COMP] = (*je)->GetStaticMethodID(je, gCLS,
			"cbComposition", "([C[BJ)V");
	jms[JMID_CAND] = (*je)->GetStaticMethodID(je, gCLS,
			"cbCandidate", "([Ljava/lang/String;III)V");
	jms[JMID_KILL] = (*je)->GetStaticMethodID(je, gCLS,
			"shouldKill", "()Z");
	jms[JMID_STAT] = (*je)->GetStaticMethodID(je, gCLS,
			"cbStatus", "(Z)V");
}
JNIEXPORT void JNICALL Java_com_yogpc_gi_w32_JNIHandler_setHWnd
		(JNIEnv * je, jclass jc, jlong ptr) {
	if (!gCLS) initGlobal(je, jc);
	if (!ptr) return;
	hWnd = (HWND)(LONG_PTR) ptr;
	WNDPROC tWndProc = (WNDPROC) GetWindowLongPtr(hWnd, GWLP_WNDPROC);
	if (tWndProc != WndProc && tWndProc) pWndProc = tWndProc;
	SetWindowLongPtr(hWnd, GWLP_WNDPROC, (LONG_PTR) WndProc);
	SetActiveWindow(hWnd);
	sendNullKeydown();// FIXME
}
static HIMC lastHIMC = NULL;
JNIEXPORT void JNICALL Java_com_yogpc_gi_w32_JNIHandler_linkIME
		(JNIEnv *je, jclass jc) {
	if (!hWnd) return;
	if (lastHIMC) {
		ImmAssociateContext(hWnd, lastHIMC);
		lastHIMC = NULL;
	}
	pushStatus();
}
JNIEXPORT void JNICALL Java_com_yogpc_gi_w32_JNIHandler_unlinkIME
		(JNIEnv *je, jclass jc) {
	if (!hWnd) return;
	HIMC hIMC = ImmAssociateContext(hWnd, 0);
	if (hIMC) lastHIMC = hIMC;
}
