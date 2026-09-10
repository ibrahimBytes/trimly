import {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  Link,
  NavLink,
  useLocation,
} from 'react-router-dom';

import {
  BarChart3,
  ChevronDown,
  FileText,
  HelpCircle,
  Link2,
  LogOut,
  Settings,
  Shield,
  User,
} from 'lucide-react';

import { useAuth } from '@/auth/AuthContext';

const NAV_LINKS = [
  {
    label: 'Links',
    href: '/',
    icon: Link2,
  },
  {
    label: 'Analytics',
    href: '/analytics',
    icon: BarChart3,
  },
  {
    label: 'Settings',
    href: '/settings',
    icon: Settings,
  },
];

function getInitials(value: string) {
  const trimmed = value.trim();

  if (!trimmed) {
    return 'U';
  }

  return trimmed.charAt(0).toUpperCase();
}

export function AppHeader() {
  const location = useLocation();

  const {
    user,
    signOut,
  } = useAuth();

  const [
    accountOpen,
    setAccountOpen,
  ] = useState(false);

  const [
    isSigningOut,
    setIsSigningOut,
  ] = useState(false);

  const accountRef =
    useRef<HTMLDivElement>(null);

  const accountButtonRef =
    useRef<HTMLButtonElement>(null);

  /*
   * ============================================================
   * AUTH GUARD
   * ============================================================
   */

  if (!user) {
    return null;
  }

  const displayName =
    user.fullName?.trim() ||
    user.email;

  const initials =
    getInitials(displayName);

  /*
   * ============================================================
   * ACCOUNT MENU BEHAVIOR
   * ============================================================
   */

  useEffect(() => {
    const handlePointerDown = (
      event: MouseEvent,
    ) => {
      if (
        accountRef.current &&
        !accountRef.current.contains(
          event.target as Node,
        )
      ) {
        setAccountOpen(false);
      }
    };

    document.addEventListener(
      'mousedown',
      handlePointerDown,
    );

    return () => {
      document.removeEventListener(
        'mousedown',
        handlePointerDown,
      );
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (
      event: KeyboardEvent,
    ) => {
      if (
        event.key !== 'Escape' ||
        !accountOpen
      ) {
        return;
      }

      setAccountOpen(false);

      requestAnimationFrame(() => {
        accountButtonRef.current?.focus();
      });
    };

    document.addEventListener(
      'keydown',
      handleKeyDown,
    );

    return () => {
      document.removeEventListener(
        'keydown',
        handleKeyDown,
      );
    };
  }, [accountOpen]);

  /*
   * Navigation should always close the account menu.
   */

  useEffect(() => {
    setAccountOpen(false);
  }, [location.pathname]);

  /*
   * ============================================================
   * SIGN OUT
   * ============================================================
   */

  async function handleSignOut() {
    if (isSigningOut) {
      return;
    }

    setIsSigningOut(true);
    setAccountOpen(false);

    try {
      await signOut();
    } finally {
      setIsSigningOut(false);
    }
  }

  /*
   * ============================================================
   * DESKTOP NAVIGATION STYLES
   * ============================================================
   */

  function getNavClassName(
    active: boolean,
  ) {
    return `
      group
      flex
      h-9
      items-center
      gap-2.5
      rounded-lg
      px-3
      text-[13px]
      font-medium
      transition-colors
      duration-150
      focus-visible:outline-none
      focus-visible:ring-2
      focus-visible:ring-[#167A45]/25
      focus-visible:ring-offset-1

      ${
        active
          ? `
            bg-[#F2FAF5]
            text-[#167A45]
          `
          : `
            text-[#6E6E73]
            hover:bg-[#F5F5F7]
            hover:text-[#1D1D1F]
          `
      }
    `;
  }

  return (
    <header
      className="
        sticky
        top-0
        z-50
        border-b
        border-[#E8E8ED]
        bg-white
      "
    >
      <div
        className="
          mx-auto
          flex
          h-14
          max-w-7xl
          items-center
          px-5
          sm:px-6
        "
      >
        {/* ==================================================
            BRAND
        ================================================== */}

        <Link
          to="/"
          aria-label="Trimly home"
          className="
            shrink-0
            rounded-md
            text-[17px]
            font-semibold
            tracking-[-0.025em]
            text-[#1D1D1F]
            focus:outline-none
            focus-visible:ring-2
            focus-visible:ring-[#167A45]/25
            focus-visible:ring-offset-2
          "
        >
          Trimly
        </Link>

        {/* ==================================================
            DESKTOP PRIMARY NAVIGATION
        ================================================== */}

        <nav
          aria-label="Primary navigation"
          className="
            ml-7
            hidden
            h-full
            items-center
            gap-1
            md:flex
          "
        >
          {NAV_LINKS.map((item) => {
            const Icon = item.icon;

            return (
              <NavLink
                key={item.href}
                to={item.href}
                end={
                  item.href === '/'
                }
                className={({ isActive }) =>
                  getNavClassName(
                    isActive,
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon
                      size={15}
                      strokeWidth={
                        isActive
                          ? 2
                          : 1.8
                      }
                      aria-hidden="true"
                    />

                    <span>
                      {item.label}
                    </span>
                  </>
                )}
              </NavLink>
            );
          })}
        </nav>

        {/* ==================================================
            ACCOUNT
            Desktop + Mobile
        ================================================== */}

        <div
          ref={accountRef}
          className="
            relative
            ml-auto
          "
        >
          <button
            ref={accountButtonRef}
            type="button"
            onClick={() =>
              setAccountOpen(
                (open) => !open,
              )
            }
            aria-label="Account menu"
            aria-expanded={accountOpen}
            aria-haspopup="menu"
            className="
              flex
              min-h-10
              items-center
              gap-2
              rounded-lg
              px-1.5
              transition-colors
              duration-150
              hover:bg-[#F5F5F7]
              focus:outline-none
              focus-visible:ring-2
              focus-visible:ring-[#167A45]/25
              focus-visible:ring-offset-1
            "
          >
            {/* Avatar */}

            <div
              className="
                flex
                h-8
                w-8
                shrink-0
                items-center
                justify-center
                rounded-full
                border
                border-[#D2D2D7]
                bg-[#F5F5F7]
                text-[12px]
                font-semibold
                text-[#374151]
              "
              aria-hidden="true"
            >
              {initials}
            </div>

            {/* Desktop identity */}

            <div
              className="
                hidden
                min-w-0
                text-left
                lg:block
              "
            >
              <p
                className="
                  max-w-[180px]
                  truncate
                  text-[12px]
                  font-medium
                  text-[#1D1D1F]
                "
              >
                {displayName}
              </p>

              <p
                className="
                  max-w-[180px]
                  truncate
                  text-[10px]
                  text-[#86868B]
                "
              >
                {user.email}
              </p>
            </div>

            <ChevronDown
              size={14}
              aria-hidden="true"
              className={`
                hidden
                text-[#86868B]
                transition-transform
                duration-150
                lg:block
                ${
                  accountOpen
                    ? 'rotate-180'
                    : ''
                }
              `}
            />
          </button>

          {/* ==================================================
              ACCOUNT DROPDOWN
          ================================================== */}

          {accountOpen && (
            <div
              role="menu"
              aria-label="Account menu"
              className="
                absolute
                right-0
                top-[calc(100%+8px)]
                w-72
                overflow-hidden
                rounded-xl
                border
                border-[#D2D2D7]
                bg-white
                shadow-[0_12px_32px_rgba(0,0,0,0.08)]
              "
            >
              {/* Identity */}

              <div
                className="
                  flex
                  items-center
                  gap-3
                  px-4
                  py-4
                "
              >
                <div
                  className="
                    flex
                    h-10
                    w-10
                    shrink-0
                    items-center
                    justify-center
                    rounded-full
                    border
                    border-[#D2D2D7]
                    bg-[#F5F5F7]
                    text-sm
                    font-semibold
                    text-[#374151]
                  "
                  aria-hidden="true"
                >
                  {initials}
                </div>

                <div className="min-w-0">
                  <p
                    className="
                      truncate
                      text-[13px]
                      font-medium
                      text-[#1D1D1F]
                    "
                  >
                    {displayName}
                  </p>

                  <p
                    className="
                      mt-0.5
                      truncate
                      text-[11px]
                      text-[#86868B]
                    "
                  >
                    {user.email}
                  </p>
                </div>
              </div>

              <div
                className="
                  h-px
                  bg-[#E8E8ED]
                "
              />

              {/* Account actions */}

              <div className="p-1.5">
                <Link
                  to="/settings"
                  role="menuitem"
                  className="
                    flex
                    min-h-10
                    items-center
                    gap-3
                    rounded-lg
                    px-3
                    text-[13px]
                    text-[#6E6E73]
                    transition-colors
                    hover:bg-[#F5F5F7]
                    hover:text-[#1D1D1F]
                    focus-visible:outline-none
                    focus-visible:ring-2
                    focus-visible:ring-[#167A45]/25
                  "
                >
                  <User
                    size={15}
                    strokeWidth={1.8}
                    aria-hidden="true"
                  />

                  Profile & settings
                </Link>

                <Link
                  to="/help"
                  role="menuitem"
                  className="
                    flex
                    min-h-10
                    items-center
                    gap-3
                    rounded-lg
                    px-3
                    text-[13px]
                    text-[#6E6E73]
                    transition-colors
                    hover:bg-[#F5F5F7]
                    hover:text-[#1D1D1F]
                    focus-visible:outline-none
                    focus-visible:ring-2
                    focus-visible:ring-[#167A45]/25
                  "
                >
                  <HelpCircle
                    size={15}
                    strokeWidth={1.8}
                    aria-hidden="true"
                  />

                  Help
                </Link>
              </div>

              <div
                className="
                  h-px
                  bg-[#E8E8ED]
                "
              />

              {/* Legal */}

              <div
                className="
                  flex
                  items-center
                  gap-4
                  px-4
                  py-3
                "
              >
                <Link
                  to="/terms"
                  role="menuitem"
                  className="
                    flex
                    items-center
                    gap-1.5
                    text-[11px]
                    text-[#86868B]
                    transition-colors
                    hover:text-[#6E6E73]
                  "
                >
                  <FileText
                    size={12}
                    aria-hidden="true"
                  />

                  Terms
                </Link>

                <Link
                  to="/privacy"
                  role="menuitem"
                  className="
                    flex
                    items-center
                    gap-1.5
                    text-[11px]
                    text-[#86868B]
                    transition-colors
                    hover:text-[#6E6E73]
                  "
                >
                  <Shield
                    size={12}
                    aria-hidden="true"
                  />

                  Privacy
                </Link>
              </div>

              <div
                className="
                  h-px
                  bg-[#E8E8ED]
                "
              />

              {/* Sign out */}

              <div className="p-1.5">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    void handleSignOut();
                  }}
                  disabled={isSigningOut}
                  aria-busy={isSigningOut}
                  className="
                    flex
                    min-h-10
                    w-full
                    items-center
                    gap-3
                    rounded-lg
                    px-3
                    text-left
                    text-[13px]
                    text-[#B42318]
                    transition-colors
                    hover:bg-[#FFF7F6]
                    focus-visible:outline-none
                    focus-visible:ring-2
                    focus-visible:ring-[#B42318]/20
                    disabled:cursor-not-allowed
                    disabled:opacity-60
                  "
                >
                  <LogOut
                    size={15}
                    strokeWidth={1.8}
                    aria-hidden="true"
                  />

                  {isSigningOut
                    ? 'Signing out…'
                    : 'Sign out'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
} 